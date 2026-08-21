package com.brika.platform.document.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.audit.AuditEvent;
import com.brika.platform.audit.AuditEventRepository;
import com.brika.platform.document.DocumentTypeRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * End-to-end security/tenant/CASE ASSIGNMENT tests for Property and Documents (Sprint 4), mirroring
 * CrmCaseEndpointsIT: real HTTP requests through the actual SecurityFilterChain/ controllers and a
 * real MinIO instance.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class DocumentEndpointsIT {

  private static final String BUCKET = "brika-documents-test";

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
  @Autowired private AuditEventRepository auditEventRepository;

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
    String body =
        objectMapper.writeValueAsString(
            new com.brika.platform.casemgmt.web.CreateCaseApiRequest("MORTGAGE"));
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
            new com.brika.platform.casemgmt.web.CreateCaseAssignmentApiRequest(
                broker.user().id(), "BROKER"));
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

  @Test
  void superadminCanManageDocumentRequirementsWithoutAnyTenant() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-dr1");

    String body =
        objectMapper.writeValueAsString(
            new CreateDocumentRequirementApiRequest("MORTGAGE", dniTypeId(), true, Map.of()));
    String response =
        mockMvc
            .perform(
                post("/api/v1/document-requirements")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.operationType").value("MORTGAGE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/document-requirements/" + id).header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void brokerCanReadButNotManageDocumentRequirements() throws Exception {
    UUID companyId = companyRepository.insert("Co DR2", "Co DR2", "TC-DR2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-dr2");

    mockMvc
        .perform(get("/api/v1/document-requirements").header("Authorization", broker.bearer()))
        .andExpect(status().isOk());

    String body =
        objectMapper.writeValueAsString(
            new CreateDocumentRequirementApiRequest("MORTGAGE", dniTypeId(), true, Map.of()));
    mockMvc
        .perform(
            post("/api/v1/document-requirements")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void listDocumentTypesReturnsSeededGlobalCatalog() throws Exception {
    UUID companyId = companyRepository.insert("Co DT1", "Co DT1", "TC-DT1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-dt1");

    mockMvc
        .perform(get("/api/v1/document-types").header("Authorization", broker.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(10)))
        .andExpect(jsonPath("$[0].code").exists())
        .andExpect(jsonPath("$[0].name").exists());
  }

  @Test
  void propertyUpsertAndGetWorkForAssignedBroker() throws Exception {
    UUID companyId = companyRepository.insert("Co PR1", "Co PR1", "TC-PR1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-pr1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-pr1");
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);

    String body =
        objectMapper.writeValueAsString(
            new com.brika.platform.property.web.UpsertPropertyApiRequest(
                Map.of("street", "Calle Mayor 1", "city", "Madrid"), "FLAT", null, null));
    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/property")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.propertyType").value("FLAT"))
        .andExpect(jsonPath("$.address.city").value("Madrid"));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/property").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.propertyType").value("FLAT"));
  }

  @Test
  void brokerWithoutCaseAssignmentCannotUpsertProperty() throws Exception {
    UUID companyId = companyRepository.insert("Co PR2", "Co PR2", "TC-PR2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-pr2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-pr2");
    UUID caseId = createCase(manager);

    String body =
        objectMapper.writeValueAsString(
            new com.brika.platform.property.web.UpsertPropertyApiRequest(
                Map.of(), "FLAT", null, null));
    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/property")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void propertyFromAnotherTenantIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co PR3A", "Co PR3A", "TC-PR3A");
    UUID companyB = companyRepository.insert("Co PR3B", "Co PR3B", "TC-PR3B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-pr3a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-pr3b");
    UUID caseBId = createCase(managerB);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseBId + "/property")
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void documentRequestCreateListAndPatch() throws Exception {
    UUID companyId = companyRepository.insert("Co DQ1", "Co DQ1", "TC-DQ1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dq1");
    UUID caseId = createCase(manager);

    String createBody =
        objectMapper.writeValueAsString(
            new CreateDocumentRequestApiRequest(dniTypeId(), null, null, null));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/document-requests")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/document-requests")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    String patchBody =
        objectMapper.writeValueAsString(new UpdateDocumentRequestApiRequest("FULFILLED"));
    mockMvc
        .perform(
            patch("/api/v1/document-requests/" + id)
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("FULFILLED"));
  }

  @Test
  void creatingADocumentRequestWithoutDocumentTypeIdReturnsAStructured400NotA500()
      throws Exception {
    // Sprint 29 (stabilization): the Angular form already requires this field, so this path is
    // only reachable by a caller talking to the API directly — but the backend must not depend on
    // the frontend for it: this used to hit a NOT NULL constraint and answer an unhandled 500.
    UUID companyId = companyRepository.insert("Co DQ2", "Co DQ2", "TC-DQ2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dq2");
    UUID caseId = createCase(manager);

    String createBody =
        objectMapper.writeValueAsString(
            new CreateDocumentRequestApiRequest(null, null, null, null));

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/document-requests")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("DOCUMENT_TYPE_ID_REQUIRED"));
  }

  @Test
  void documentUploadReviewPublishAndDownloadFullCycle() throws Exception {
    UUID companyId = companyRepository.insert("Co DC1", "Co DC1", "TC-DC1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dc1");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);

    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "hello".getBytes());
    String uploadResponse =
        mockMvc
            .perform(
                multipart("/api/v1/documents/" + documentId + "/versions")
                    .file(file)
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versionNumber").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID versionId = UUID.fromString(objectMapper.readTree(uploadResponse).get("id").asText());

    String reviewBody =
        objectMapper.writeValueAsString(new ReviewDocumentApiRequest("APPROVED", null));
    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/review")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reviewBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reviewStatus").value("APPROVED"));

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/publish")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/documents/" + documentId + "/download")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").exists());

    mockMvc
        .perform(
            get("/api/v1/documents/" + documentId + "/versions/" + versionId + "/download")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").exists());

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/unpublish")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    List<AuditEvent> events =
        auditEventRepository.findAll().stream()
            .filter(e -> documentId.equals(e.resourceId()))
            .toList();
    assertThat(events).extracting(AuditEvent::action).contains("DOCUMENT_VERSION_UPLOADED");
    assertThat(events).extracting(AuditEvent::action).contains("DOCUMENT_REVIEWED");
    assertThat(events).filteredOn(e -> "DOCUMENT_DOWNLOADED".equals(e.action())).hasSize(2);
    assertThat(events).allMatch(e -> companyId.equals(e.companyId()));
    assertThat(events).allMatch(e -> manager.user().id().equals(e.actorUserId()));
  }

  @Test
  void documentUploadRejectsUnsupportedMimeType() throws Exception {
    UUID companyId = companyRepository.insert("Co DC2", "Co DC2", "TC-DC2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dc2");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);

    MockMultipartFile file =
        new MockMultipartFile("file", "bad.exe", "application/x-msdownload", "x".getBytes());
    mockMvc
        .perform(
            multipart("/api/v1/documents/" + documentId + "/versions")
                .file(file)
                .header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void brokerWithoutCaseAssignmentCannotUploadDocumentVersion() throws Exception {
    UUID companyId = companyRepository.insert("Co DC3", "Co DC3", "TC-DC3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dc3");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-dc3");
    UUID caseId = createCase(manager);
    UUID documentId = createDocument(manager, caseId);

    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "hello".getBytes());
    mockMvc
        .perform(
            multipart("/api/v1/documents/" + documentId + "/versions")
                .file(file)
                .header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void documentFromAnotherTenantIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co DC4A", "Co DC4A", "TC-DC4A");
    UUID companyB = companyRepository.insert("Co DC4B", "Co DC4B", "TC-DC4B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-dc4a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-dc4b");
    UUID caseBId = createCase(managerB);
    UUID documentBId = createDocument(managerB, caseBId);

    mockMvc
        .perform(get("/api/v1/documents/" + documentBId).header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void superadminWithoutSupportSessionCannotAccessDocumentsEndpoint() throws Exception {
    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — documents are case-scoped and the tenant is
    // resolved from the case, so the endpoint is now accessible (200, empty list).
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-dc5");
    UUID companyId = companyRepository.insert("Co DC5", "Co DC5", "TC-DC5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dc5");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/documents")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void clientCannotAccessInternalDocumentsEndpoint() throws Exception {
    UUID companyId = companyRepository.insert("Co DC6", "Co DC6", "TC-DC6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dc6");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-dc6");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/documents").header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }
}
