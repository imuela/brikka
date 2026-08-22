package com.brika.platform.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.web.CreateDocumentApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

/**
 * BUG-002 (Sprint 33): reproduces, with the real {@code HttpAiTaskDispatcher} enabled end-to-end
 * against a real HTTP fake-Worker server, the exact race that a synchronous {@code @Transactional}
 * on {@code DocumentExtractionService.request} used to cause — a Worker fast enough to call back
 * BEFORE the dispatching request's own transaction commits could not see the just-inserted PENDING
 * row yet. This fake worker calls back synchronously, from inside its own HTTP handler, before
 * responding to the dispatch — the tightest possible race, tighter than any real network round-trip
 * would ever produce. Needs a real bound server port (not MockMvc's in-process dispatch) so the
 * external fake worker can actually reach this JVM's callback endpoint — {@code server.port} is
 * pinned to a pre-chosen free port before context startup so {@code brika.ai.callback-base-url} can
 * reference the same port up front.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class HttpDispatchTransactionRaceIT {

  private static final String BUCKET = "brika-documents-race-test";
  private static final String CALLBACK_SECRET = "race-test-secret";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @Container
  static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

  private static HttpServer fakeWorker;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) throws IOException {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("brika.storage.endpoint", MINIO::getS3URL);
    registry.add("brika.storage.access-key", MINIO::getUserName);
    registry.add("brika.storage.secret-key", MINIO::getPassword);
    registry.add("brika.storage.bucket", () -> BUCKET);
    createBucket();

    int appPort = findFreePort();
    registry.add("server.port", () -> appPort);

    fakeWorker = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    int fakeWorkerPort = fakeWorker.getAddress().getPort();
    fakeWorker.createContext(
        "/extract",
        exchange -> {
          // The tightest possible race: call back to Spring Boot's own callback endpoint
          // SYNCHRONOUSLY, before this handler even responds 202 to the original dispatch call —
          // if DocumentExtractionService.request were still @Transactional, that transaction
          // would still be open right now, in the middle of its own synchronous HTTP send().
          byte[] body = exchange.getRequestBody().readAllBytes();
          Map<?, ?> envelope = new ObjectMapper().readValue(body, Map.class);
          Map<?, ?> payload = (Map<?, ?>) envelope.get("payload");
          String callbackUrl = (String) payload.get("callbackUrl");

          HttpClient client = HttpClient.newHttpClient();
          String callbackBody = "{\"extractedFields\":[],\"confidence\":{}}";
          try {
            client.send(
                HttpRequest.newBuilder(URI.create(callbackUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Ai-Worker-Secret", CALLBACK_SECRET)
                    .POST(HttpRequest.BodyPublishers.ofString(callbackBody))
                    .build(),
                HttpResponse.BodyHandlers.discarding());
          } catch (Exception e) {
            throw new IOException(e);
          }

          byte[] response = "{\"status\":\"ACCEPTED\"}".getBytes();
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(202, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    fakeWorker.start();

    registry.add("brika.ai.worker-transport", () -> "http");
    registry.add("brika.ai.worker-url", () -> "http://127.0.0.1:" + fakeWorkerPort);
    registry.add("brika.ai.callback-base-url", () -> "http://localhost:" + appPort);
    registry.add("brika.ai.worker-callback-secret", () -> CALLBACK_SECRET);
  }

  private static int findFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    }
  }

  private static void createBucket() {
    try (S3Client client =
        S3Client.builder()
            .endpointOverride(URI.create(MINIO.getS3URL()))
            .region(Region.US_EAST_1)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
            .forcePathStyle(true)
            .build()) {
      client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }
  }

  @AfterEach
  void stopFakeWorker() {
    if (fakeWorker != null) {
      fakeWorker.stop(0);
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private DocumentTypeRepository documentTypeRepository;
  @Autowired private DataSource dataSource;

  private record TestPrincipal(String externalIdentityId, User user) {
    String bearer() {
      return "Bearer " + externalIdentityId;
    }
  }

  private TestPrincipal createUser(UserRole role, UUID companyId, String emailPrefix) {
    String externalId = "ext-" + UUID.randomUUID();
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                role, companyId, externalId, emailPrefix + "@brika.test", "First", "Last"));
    return new TestPrincipal(externalId, user);
  }

  private UUID dniTypeId() {
    return documentTypeRepository.findAll().stream()
        .filter(t -> t.code().equals("DNI"))
        .findFirst()
        .orElseThrow()
        .id();
  }

  @Test
  void aWorkerThatCallsBackBeforeRespondingNeverCrashesTheCallbackWithANoSuchElementException()
      throws Exception {
    UUID companyId = companyRepository.insert("Co RACE1", "Co RACE1", "TC-RACE1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-race1");

    String caseBody = objectMapper.writeValueAsString(new CreateCaseApiRequest("MORTGAGE"));
    String caseResponse =
        mockMvc
            .perform(
                post("/api/v1/cases")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(caseBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID caseId = UUID.fromString(objectMapper.readTree(caseResponse).get("id").asText());

    String docBody = objectMapper.writeValueAsString(new CreateDocumentApiRequest(dniTypeId()));
    String docResponse =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/documents")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(docBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID documentId = UUID.fromString(objectMapper.readTree(docResponse).get("id").asText());

    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "hello".getBytes());
    String versionResponse =
        mockMvc
            .perform(
                MockMvcRequestBuilders.multipart("/api/v1/documents/" + documentId + "/versions")
                    .file(file)
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID versionId = UUID.fromString(objectMapper.readTree(versionResponse).get("id").asText());

    String extractionBody =
        objectMapper.writeValueAsString(
            new com.brika.platform.ai.web.CreateDocumentExtractionApiRequest(versionId));
    String extractionResponse =
        mockMvc
            .perform(
                post("/api/v1/documents/" + documentId + "/ai/document-extractions")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(extractionBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID extractionId =
        UUID.fromString(objectMapper.readTree(extractionResponse).get("id").asText());

    // The old bug left the row stuck at PENDING forever (the callback crashed with
    // NoSuchElementException from the dispatcher's perspective — the original synchronous dispatch
    // call never throws on that, since it never inspects the worker's response — so the original
    // request still returned 200). The fix makes this converge reliably instead. Poll manually
    // (no test-only polling library dependency in this project) with a generous deadline — the
    // fake worker's callback is already synchronous from the dispatch call's own perspective, so
    // in practice this resolves on the very first check.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String status = null;
    long deadline = System.currentTimeMillis() + Duration.ofSeconds(5).toMillis();
    while (System.currentTimeMillis() < deadline) {
      status =
          jdbc.queryForObject(
              "SELECT status FROM document_extractions WHERE id = ?", String.class, extractionId);
      if (!"PENDING".equals(status)) {
        break;
      }
      Thread.sleep(50);
    }
    assertThat(status).isEqualTo("NO_PROVIDER");
  }
}
