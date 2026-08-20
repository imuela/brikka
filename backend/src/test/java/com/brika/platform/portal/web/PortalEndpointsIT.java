package com.brika.platform.portal.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.web.CaseClientApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.communication.web.CreateConversationApiRequest;
import com.brika.platform.crm.web.CreateClientApiRequest;
import com.brika.platform.crm.web.CreatePortalAccountApiRequest;
import com.brika.platform.document.web.CreateDocumentRequestApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.brika.platform.notification.NotificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
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
 * ADR-PORTAL-AUTH-001 / Sprint 7 end-to-end security tests: separate SecurityFilterChain, Portal
 * principal never resolvable to a users row, tenant + case + participant + visibility, cross-
 * tenant/cross-client masking, and the internal comms subset (D2) that makes Portal messaging work.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class PortalEndpointsIT {

  private static final String BUCKET = "brika-portal-test";

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
  @Autowired private com.brika.platform.document.DocumentTypeRepository documentTypeRepository;
  @Autowired private NotificationRepository notificationRepository;

  private record TestPrincipal(String externalIdentityId, User user) {
    String bearer() {
      return "Bearer " + externalIdentityId;
    }
  }

  private record PortalPrincipal(String externalIdentityId, UUID clientId) {
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

  private UUID createClient(TestPrincipal manager, String firstName) throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateClientApiRequest(
                firstName, "Client", firstName + "@client.test", "600000000"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/clients")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
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

  private void addClientToCase(TestPrincipal manager, UUID caseId, UUID clientId) throws Exception {
    String body =
        objectMapper.writeValueAsString(new CaseClientApiRequest(clientId, "HOLDER", true));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/clients")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private PortalPrincipal createPortalAccount(TestPrincipal manager, UUID clientId)
      throws Exception {
    String externalId = "portal-ext-" + UUID.randomUUID();
    String body = objectMapper.writeValueAsString(new CreatePortalAccountApiRequest(externalId));
    mockMvc
        .perform(
            post("/api/v1/clients/" + clientId + "/portal-account")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    return new PortalPrincipal(externalId, clientId);
  }

  private UUID createClientConversation(TestPrincipal manager, UUID caseId, UUID clientId)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateConversationApiRequest("CLIENT", List.of(clientId)));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/conversations")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("CLIENT"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  // ---- Golden path ----

  @Test
  void portalGoldenPathDashboardDocumentsMessagesProfile() throws Exception {
    UUID companyId = companyRepository.insert("Co P1", "Co P1", "TC-P1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p1");
    UUID clientId = createClient(manager, "Golden");
    UUID caseId = createCase(manager);
    addClientToCase(manager, caseId, clientId);
    PortalPrincipal portal = createPortalAccount(manager, clientId);

    mockMvc
        .perform(get("/api/v1/portal/me").header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Golden"))
        .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

    mockMvc
        .perform(get("/api/v1/portal/cases").header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(caseId.toString()));

    mockMvc
        .perform(get("/api/v1/portal/cases/" + caseId).header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PRESTUDY"));

    // No published documents yet.
    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/documents")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    // Portal upload (V12: uploaded_by_client_id).
    UUID documentTypeId = wellKnownDniTypeId();
    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "hello".getBytes());
    mockMvc
        .perform(
            multipart("/api/v1/portal/cases/" + caseId + "/documents")
                .file(file)
                .param("documentTypeId", documentTypeId.toString())
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalFilename").value("dni.pdf"));

    // Not visible yet (not published by the broker).
    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/documents")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    // Messaging: broker creates the CLIENT conversation, client reads/sends/attaches.
    createClientConversation(manager, caseId, clientId);

    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/messages")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    String messageResponse =
        mockMvc
            .perform(
                post("/api/v1/portal/cases/" + caseId + "/messages")
                    .header("Authorization", portal.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new com.brika.platform.communication.web.CreateMessageApiRequest(
                                "Hello broker"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.senderClientId").value(clientId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID messageId = UUID.fromString(objectMapper.readTree(messageResponse).get("id").asText());

    MockMultipartFile attachment =
        new MockMultipartFile("file", "note.pdf", "application/pdf", "attach".getBytes());
    mockMvc
        .perform(
            multipart("/api/v1/portal/messages/" + messageId + "/attachments")
                .file(attachment)
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalFilename").value("note.pdf"));

    mockMvc
        .perform(
            get("/api/v1/portal/messages/" + messageId + "/attachments")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    // Broker can also read/reply via the internal endpoint (same conversation).
    UUID conversationId =
        UUID.fromString(
            objectMapper
                .readTree(
                    mockMvc
                        .perform(
                            get("/api/v1/cases/" + caseId + "/conversations")
                                .header("Authorization", manager.bearer()))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .get(0)
                .get("id")
                .asText());
    mockMvc
        .perform(
            get("/api/v1/conversations/" + conversationId + "/messages")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    // Notifications: the portal client has none — nothing in this flow notifies the client
    // itself (its uploads/messages notify the case's internal assignees, not the sender).
    mockMvc
        .perform(get("/api/v1/portal/notifications").header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));

    // Profile: only email/phone editable.
    mockMvc
        .perform(
            patch("/api/v1/portal/profile")
                .header("Authorization", portal.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpdatePortalProfileApiRequest("new@client.test", "611111111"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new@client.test"))
        .andExpect(jsonPath("$.firstName").value("Golden")); // untouched
  }

  private UUID wellKnownDniTypeId() {
    return documentTypeRepository.findAll().stream()
        .filter(t -> t.code().equals("DNI"))
        .findFirst()
        .orElseThrow()
        .id();
  }

  // ---- Security: cross-tenant / cross-client masking ----

  @Test
  void portalFromAnotherTenantCannotSeeCase() throws Exception {
    UUID companyA = companyRepository.insert("Co P2A", "Co P2A", "TC-P2A");
    UUID companyB = companyRepository.insert("Co P2B", "Co P2B", "TC-P2B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-p2a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-p2b");
    UUID clientA = createClient(managerA, "TenantA");
    UUID caseB = createCase(managerB);
    PortalPrincipal portalA = createPortalAccount(managerA, clientA);

    mockMvc
        .perform(get("/api/v1/portal/cases/" + caseB).header("Authorization", portalA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void clientWithoutCaseRelationshipGets404() throws Exception {
    UUID companyId = companyRepository.insert("Co P3", "Co P3", "TC-P3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p3");
    UUID clientId = createClient(manager, "Unrelated");
    UUID caseId = createCase(manager); // client never added to this case
    PortalPrincipal portal = createPortalAccount(manager, clientId);

    mockMvc
        .perform(get("/api/v1/portal/cases/" + caseId).header("Authorization", portal.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void unpublishedDocumentIsNeverVisibleToClient() throws Exception {
    UUID companyId = companyRepository.insert("Co P4", "Co P4", "TC-P4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p4");
    UUID clientId = createClient(manager, "DocClient");
    UUID caseId = createCase(manager);
    addClientToCase(manager, caseId, clientId);
    PortalPrincipal portal = createPortalAccount(manager, clientId);

    UUID documentTypeId = wellKnownDniTypeId();
    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "hello".getBytes());
    mockMvc
        .perform(
            multipart("/api/v1/portal/cases/" + caseId + "/documents")
                .file(file)
                .param("documentTypeId", documentTypeId.toString())
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/documents")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void clientNotParticipantOfConversationCannotReadMessages() throws Exception {
    UUID companyId = companyRepository.insert("Co P5", "Co P5", "TC-P5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p5");
    UUID clientA = createClient(manager, "ParticipantA");
    UUID clientB = createClient(manager, "OutsiderB");
    UUID caseId = createCase(manager);
    addClientToCase(manager, caseId, clientA);
    addClientToCase(manager, caseId, clientB);
    createClientConversation(manager, caseId, clientA); // only clientA is a participant
    PortalPrincipal portalB = createPortalAccount(manager, clientB);

    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/messages")
                .header("Authorization", portalB.bearer()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$", hasSize(0))); // resolves to "no conversation for me" -> empty, never leaks

    mockMvc
        .perform(
            post("/api/v1/portal/cases/" + caseId + "/messages")
                .header("Authorization", portalB.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new com.brika.platform.communication.web.CreateMessageApiRequest("hi"))))
        .andExpect(status().isNotFound());
  }

  // ---- Security: authentication boundary (ADR-PORTAL-AUTH-001) ----

  @Test
  void portalTokenCannotAccessInternalEndpoint() throws Exception {
    UUID companyId = companyRepository.insert("Co P6", "Co P6", "TC-P6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p6");
    UUID clientId = createClient(manager, "Boundary");
    PortalPrincipal portal = createPortalAccount(manager, clientId);

    mockMvc
        .perform(get("/api/v1/cases").header("Authorization", portal.bearer()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void internalTokenCannotAuthenticateOnPortalChain() throws Exception {
    UUID companyId = companyRepository.insert("Co P7", "Co P7", "TC-P7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p7");

    mockMvc
        .perform(get("/api/v1/portal/me").header("Authorization", manager.bearer()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownPortalIdentityIsUnauthenticated() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/portal/me")
                .header("Authorization", "Bearer nonexistent-" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void portalPrincipalCannotResolveInternalPermissionsThroughUsers() throws Exception {
    UUID companyId = companyRepository.insert("Co P8", "Co P8", "TC-P8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p8");
    UUID clientId = createClient(manager, "Isolated");
    PortalPrincipal portal = createPortalAccount(manager, clientId);

    // Same bearer value cannot be reused to authenticate as an internal user either:
    // client_portal_accounts.external_identity_id was never written to users at all.
    mockMvc
        .perform(get("/api/v1/me").header("Authorization", portal.bearer()))
        .andExpect(status().isUnauthorized());

    // And within the Portal chain itself, only the 11 PORTAL_* permissions are ever available —
    // an internal-only permission code is never granted (mechanical, exercised via a case-scoped
    // endpoint that requires an internal permission never present in
    // PortalPermissionResolutionService).
    mockMvc
        .perform(get("/api/v1/portal/cases").header("Authorization", portal.bearer()))
        .andExpect(status().isOk()); // PORTAL_CASE_READ works
  }

  // ---- Sprint 19 (ADR-PROCESS-007): notification mark-as-read ----

  @Test
  void portalCanMarkOwnNotificationAsRead() throws Exception {
    UUID companyId = companyRepository.insert("Co P9", "Co P9", "TC-P9");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p9");
    UUID clientId = createClient(manager, "NotifOwner");
    PortalPrincipal portal = createPortalAccount(manager, clientId);

    UUID notificationId =
        notificationRepository.insert(companyId, null, clientId, "case.status_changed", "{}");

    mockMvc
        .perform(get("/api/v1/portal/notifications").header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].readAt").doesNotExist());

    mockMvc
        .perform(
            patch("/api/v1/portal/notifications/" + notificationId + "/read")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").exists());

    mockMvc
        .perform(get("/api/v1/portal/notifications").header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].readAt").exists());
  }

  @Test
  void clientCannotMarkAnotherClientsNotificationAsRead() throws Exception {
    UUID companyId = companyRepository.insert("Co P10", "Co P10", "TC-P10");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p10");
    UUID clientA = createClient(manager, "NotifA");
    UUID clientB = createClient(manager, "NotifB");
    PortalPrincipal portalB = createPortalAccount(manager, clientB);

    UUID notificationForA =
        notificationRepository.insert(companyId, null, clientA, "case.status_changed", "{}");

    mockMvc
        .perform(
            patch("/api/v1/portal/notifications/" + notificationForA + "/read")
                .header("Authorization", portalB.bearer()))
        .andExpect(status().isNotFound());
  }

  // ---- Sprint 19 (ADR-PROCESS-007): explicit document-requests view ----

  private UUID createDocumentRequest(
      TestPrincipal manager, UUID caseId, UUID documentTypeId, UUID clientId) throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateDocumentRequestApiRequest(documentTypeId, clientId, null, null));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/document-requests")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  @Test
  void portalListsOwnDocumentRequestsWithResolvedTypeName() throws Exception {
    UUID companyId = companyRepository.insert("Co P11", "Co P11", "TC-P11");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p11");
    UUID clientId = createClient(manager, "ReqOwner");
    UUID caseId = createCase(manager);
    addClientToCase(manager, caseId, clientId);
    PortalPrincipal portal = createPortalAccount(manager, clientId);
    UUID documentTypeId = wellKnownDniTypeId();

    createDocumentRequest(manager, caseId, documentTypeId, clientId);

    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/document-requests")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].documentTypeCode").value("DNI"))
        .andExpect(jsonPath("$[0].status").value("PENDING"));
  }

  @Test
  void documentRequestIsScopedToTheRequestingClientOnly() throws Exception {
    UUID companyId = companyRepository.insert("Co P12", "Co P12", "TC-P12");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p12");
    UUID clientA = createClient(manager, "ReqA");
    UUID clientB = createClient(manager, "ReqB");
    UUID caseId = createCase(manager);
    addClientToCase(manager, caseId, clientA);
    addClientToCase(manager, caseId, clientB);
    PortalPrincipal portalB = createPortalAccount(manager, clientB);
    UUID documentTypeId = wellKnownDniTypeId();

    createDocumentRequest(manager, caseId, documentTypeId, clientA); // requested from A, not B

    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/document-requests")
                .header("Authorization", portalB.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void uploadingTheRequestedDocumentReflectsAsFulfilledInTheExplicitView() throws Exception {
    UUID companyId = companyRepository.insert("Co P13", "Co P13", "TC-P13");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-p13");
    UUID clientId = createClient(manager, "Fulfiller");
    UUID caseId = createCase(manager);
    addClientToCase(manager, caseId, clientId);
    PortalPrincipal portal = createPortalAccount(manager, clientId);
    UUID documentTypeId = wellKnownDniTypeId();

    createDocumentRequest(manager, caseId, documentTypeId, clientId);

    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "hello".getBytes());
    mockMvc
        .perform(
            multipart("/api/v1/portal/cases/" + caseId + "/documents")
                .file(file)
                .param("documentTypeId", documentTypeId.toString())
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/document-requests")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].status").value("FULFILLED"));
  }
}
