package com.brika.platform.ai.web;

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
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
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
 * ADR-AI-001: internal Worker callback endpoint, outside /api/v1, protected by a shared secret
 * checked manually (never part of the JWT-based SecurityConfig chains) — see the `/internal/ai/**`
 * permitAll wiring in SecurityConfig, required so the (network-isolated, credential-less) Python
 * Worker can actually reach this endpoint.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class AiExtractionCallbackEndpointsIT {

  private static final String BUCKET = "brika-documents-callback-test";
  private static final String SECRET = "test-worker-secret";

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @Container
  static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2024-01-16T16-07-38Z");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("brika.storage.endpoint", MINIO::getS3URL);
    registry.add("brika.storage.access-key", MINIO::getUserName);
    registry.add("brika.storage.secret-key", MINIO::getPassword);
    registry.add("brika.storage.bucket", () -> BUCKET);
    registry.add("brika.ai.worker-callback-secret", () -> SECRET);
    createBucket();
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

  private UUID createCase(TestPrincipal creator) throws Exception {
    String body = objectMapper.writeValueAsString(new CreateCaseApiRequest("MORTGAGE"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases")
                    .header("Authorization", creator.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private UUID createDocument(TestPrincipal principal, UUID caseId) throws Exception {
    String body = objectMapper.writeValueAsString(new CreateDocumentApiRequest(dniTypeId()));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/documents")
                    .header("Authorization", principal.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private UUID uploadVersion(TestPrincipal principal, UUID documentId) throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "hello".getBytes());
    String response =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                        "/api/v1/documents/" + documentId + "/versions")
                    .file(file)
                    .header("Authorization", principal.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private UUID createPendingExtraction(TestPrincipal principal, UUID documentId, UUID versionId)
      throws Exception {
    // Extraction is inserted directly (bypassing dispatch) so its status starts PENDING, letting
    // the callback test drive the transition explicitly rather than relying on
    // LocalAiTaskDispatcher having already resolved it synchronously.
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    UUID companyId = principal.user().companyId();
    return jdbc.queryForObject(
        "INSERT INTO document_extractions (company_id, document_version_id, status, provider,"
            + " model) VALUES (?, ?, 'PENDING', 'none', 'none') RETURNING id",
        UUID.class,
        companyId,
        versionId);
  }

  @Test
  void callbackWithCorrectSecretAppliesResult() throws Exception {
    UUID companyId = companyRepository.insert("Co CB1", "Co CB1", "TC-CB1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cb1");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);
    UUID extractionId = createPendingExtraction(manager, documentId, versionId);

    String body =
        objectMapper.writeValueAsString(new WorkerCallbackApiRequest(List.of(), Map.of()));
    mockMvc
        .perform(
            post("/internal/ai/document-extractions/" + extractionId + "/callback")
                .header("X-Ai-Worker-Secret", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String status =
        jdbc.queryForObject(
            "SELECT status FROM document_extractions WHERE id = ?", String.class, extractionId);
    assertThat(status).isEqualTo("NO_PROVIDER");
  }

  @Test
  void callbackWithWrongSecretIsRejectedAndDoesNotApplyResult() throws Exception {
    UUID companyId = companyRepository.insert("Co CB2", "Co CB2", "TC-CB2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cb2");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);
    UUID extractionId = createPendingExtraction(manager, documentId, versionId);

    String body =
        objectMapper.writeValueAsString(new WorkerCallbackApiRequest(List.of(), Map.of()));
    mockMvc
        .perform(
            post("/internal/ai/document-extractions/" + extractionId + "/callback")
                .header("X-Ai-Worker-Secret", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String status =
        jdbc.queryForObject(
            "SELECT status FROM document_extractions WHERE id = ?", String.class, extractionId);
    assertThat(status).isEqualTo("PENDING");
  }

  @Test
  void callbackWithoutSecretHeaderIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co CB3", "Co CB3", "TC-CB3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cb3");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);
    UUID extractionId = createPendingExtraction(manager, documentId, versionId);

    String body =
        objectMapper.writeValueAsString(new WorkerCallbackApiRequest(List.of(), Map.of()));
    mockMvc
        .perform(
            post("/internal/ai/document-extractions/" + extractionId + "/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void callbackDoesNotRequireAnyAuthorizationBearerToken() throws Exception {
    // The Worker has no JWT — SecurityConfig must permitAll /internal/ai/** so only the
    // shared-secret check (not OAuth2 resource server authentication) gates this endpoint.
    UUID companyId = companyRepository.insert("Co CB4", "Co CB4", "TC-CB4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cb4");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);
    UUID extractionId = createPendingExtraction(manager, documentId, versionId);

    String body =
        objectMapper.writeValueAsString(new WorkerCallbackApiRequest(List.of(), Map.of()));
    mockMvc
        .perform(
            post("/internal/ai/document-extractions/" + extractionId + "/callback")
                .header("X-Ai-Worker-Secret", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }
}
