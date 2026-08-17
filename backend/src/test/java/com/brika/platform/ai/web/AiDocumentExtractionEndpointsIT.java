package com.brika.platform.ai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseAssignmentApiRequest;
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
 * 21_AI_V1_SCOPE.md §2.A / Sprint 10 D10-1/D10-2/D10-5: document extraction end-to-end, exercised
 * through the real SecurityFilterChain/controllers, mirroring DocumentEndpointsIT's real-MinIO
 * setup. LocalAiTaskDispatcher (default transport) resolves every extraction synchronously and
 * honestly to NO_PROVIDER — never a fabricated result (D10-2).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class AiDocumentExtractionEndpointsIT {

  private static final String BUCKET = "brika-documents-ai-test";

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

  private void assignBroker(TestPrincipal manager, TestPrincipal broker, UUID caseId)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateCaseAssignmentApiRequest(broker.user().id(), "BROKER"));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/assignments")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
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
                multipart("/api/v1/documents/" + documentId + "/versions")
                    .file(file)
                    .header("Authorization", principal.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  @Test
  void extractionResolvesSynchronouslyToHonestNoProviderOutcome() throws Exception {
    UUID companyId = companyRepository.insert("Co AI1", "Co AI1", "TC-AI1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ai1");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);

    String body =
        objectMapper.writeValueAsString(new CreateDocumentExtractionApiRequest(versionId));
    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/ai/document-extractions")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NO_PROVIDER"))
        .andExpect(jsonPath("$.provider").value("none"))
        .andExpect(jsonPath("$.model").value("none"))
        .andExpect(jsonPath("$.documentVersionId").value(versionId.toString()));
  }

  @Test
  void documentVersionNotBelongingToDocumentIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co AI2", "Co AI2", "TC-AI2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ai2");
    UUID caseId = createCase(manager);
    UUID documentA = createDocument(manager, caseId);
    UUID documentB = createDocument(manager, caseId);
    UUID versionOfB = uploadVersion(manager, documentB);

    String body =
        objectMapper.writeValueAsString(new CreateDocumentExtractionApiRequest(versionOfB));
    mockMvc
        .perform(
            post("/api/v1/documents/" + documentA + "/ai/document-extractions")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void listAndGetReturnCreatedExtraction() throws Exception {
    UUID companyId = companyRepository.insert("Co AI3", "Co AI3", "TC-AI3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ai3");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);

    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/documents/" + documentId + "/ai/document-extractions")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateDocumentExtractionApiRequest(versionId))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID extractionId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/documents/" + documentId + "/ai/document-extractions")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(extractionId.toString()));

    mockMvc
        .perform(
            get("/api/v1/ai/document-extractions/" + extractionId)
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("NO_PROVIDER"));
  }

  @Test
  void aiUsageIsLoggedForEveryExtraction() throws Exception {
    UUID companyId = companyRepository.insert("Co AI4", "Co AI4", "TC-AI4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ai4");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/ai/document-extractions")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateDocumentExtractionApiRequest(versionId))))
        .andExpect(status().isOk());

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Integer usageCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ai_usage WHERE company_id = ? AND operation = 'DOCUMENT_EXTRACTION'"
                + " AND provider = 'none' AND model = 'none'",
            Integer.class,
            companyId);
    assertThat(usageCount).isEqualTo(1);
  }

  @Test
  void brokerWithoutCaseAssignmentCannotRequestExtraction() throws Exception {
    UUID companyId = companyRepository.insert("Co AI5", "Co AI5", "TC-AI5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ai5");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-ai5");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/ai/document-extractions")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateDocumentExtractionApiRequest(versionId))))
        .andExpect(status().isForbidden());
  }

  @Test
  void brokerWithCaseAssignmentCanRequestExtraction() throws Exception {
    UUID companyId = companyRepository.insert("Co AI6", "Co AI6", "TC-AI6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ai6");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-ai6");
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/ai/document-extractions")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateDocumentExtractionApiRequest(versionId))))
        .andExpect(status().isOk());
  }

  @Test
  void clientCannotRequestExtraction() throws Exception {
    UUID companyId = companyRepository.insert("Co AI7", "Co AI7", "TC-AI7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ai7");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-ai7");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/ai/document-extractions")
                .header("Authorization", client.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateDocumentExtractionApiRequest(versionId))))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminWithoutSupportSessionCannotRequestExtraction() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ai8");
    UUID companyId = companyRepository.insert("Co AI8", "Co AI8", "TC-AI8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ai8");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);
    UUID versionId = uploadVersion(manager, documentId);

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/ai/document-extractions")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateDocumentExtractionApiRequest(versionId))))
        .andExpect(status().isForbidden());
  }

  @Test
  void extractionFromAnotherTenantDocumentIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co AI9A", "Co AI9A", "TC-AI9A");
    UUID companyB = companyRepository.insert("Co AI9B", "Co AI9B", "TC-AI9B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-ai9a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-ai9b");
    UUID caseB = createCase(managerB);
    UUID documentB = createDocument(managerB, caseB);
    UUID versionB = uploadVersion(managerB, documentB);

    mockMvc
        .perform(
            get("/api/v1/documents/" + documentB + "/ai/document-extractions")
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());

    String createResponse =
        mockMvc
            .perform(
                post("/api/v1/documents/" + documentB + "/ai/document-extractions")
                    .header("Authorization", managerB.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new CreateDocumentExtractionApiRequest(versionB))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID extractionId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/ai/document-extractions/" + extractionId)
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }
}
