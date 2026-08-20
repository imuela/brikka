package com.brika.platform.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.ai.web.AiUseCaseApiRequest;
import com.brika.platform.audit.AuditEvent;
import com.brika.platform.audit.AuditEventRepository;
import com.brika.platform.bank.web.CreateBankApiRequest;
import com.brika.platform.bank.web.CreateBankContactApiRequest;
import com.brika.platform.bank.web.CreateBankCriteriaVersionApiRequest;
import com.brika.platform.bankrequest.web.CreateBankRequestApiRequest;
import com.brika.platform.casemgmt.web.CaseClientApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.crm.web.CreateClientApiRequest;
import com.brika.platform.crm.web.CreatePortalAccountApiRequest;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.web.CreateDocumentApiRequest;
import com.brika.platform.document.web.ReviewDocumentApiRequest;
import com.brika.platform.financing.web.CreateFinancingRequestApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.brika.platform.property.web.UpsertPropertyApiRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
 * Sprint 12 D12-4: three cross-module, API-level (no UI) end-to-end flows, each exercised through
 * real HTTP requests against the actual SecurityFilterChain/controllers, mirroring the per-module
 * ITs this suite composes. No new business logic — only sequences of already-tested endpoints.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class CrossModuleE2EIT {

  private static final String BUCKET = "brika-e2e-test";

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

  private record PortalPrincipal(String externalIdentityId) {
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

  private UUID dniTypeId() {
    return documentTypeRepository.findAll().stream()
        .filter(t -> t.code().equals("DNI"))
        .findFirst()
        .orElseThrow()
        .id();
  }

  // ==================================================================================
  // Flow 1: casemgmt -> property -> financing -> scoring -> bankmatching -> bankrequest
  // ==================================================================================

  @Test
  void mortgageLifecycleFlowEndToEnd() throws Exception {
    UUID companyId = companyRepository.insert("Co E2E1", "Co E2E1", "TC-E2E1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-e2e1");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-e2e1");

    // 1. Case
    UUID caseId = createCase(manager);

    // 2. Property
    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/property")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpsertPropertyApiRequest(
                            Map.of("city", "Madrid"),
                            "FLAT",
                            new BigDecimal("300000"),
                            new BigDecimal("280000")))))
        .andExpect(status().isOk());

    // 3. Financing request
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financing-requests")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateFinancingRequestApiRequest(new BigDecimal("200000"), 240))))
        .andExpect(status().isOk());

    // 4. Scoring (requires an active ruleset, seeded here by SUPERADMIN)
    String rulesetBody =
        objectMapper.writeValueAsString(
            Map.of(
                "code",
                "E2E1_RULESET",
                "version",
                "v1",
                "categories",
                List.of(category("LOW", 0), category("HIGH", null)),
                "rules",
                List.of(
                    Map.of(
                        "code", "term-long",
                        "weight", 10,
                        "field", "financingRequest.termMonths",
                        "operator", "GREATER_THAN_OR_EQUAL",
                        "value", 1))));
    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(rulesetBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/scoring/run")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    // 5. Bank matching (bank + active criteria seeded by SUPERADMIN, matching run by MANAGER)
    String bankBody =
        objectMapper.writeValueAsString(new CreateBankApiRequest("E2E1", "Bank E2E1", Map.of()));
    String bankResponse =
        mockMvc
            .perform(
                post("/api/v1/banks")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bankBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID bankId = UUID.fromString(objectMapper.readTree(bankResponse).get("id").asText());

    Map<String, Object> ltvRule =
        Map.of(
            "id", "max-ltv-80",
            "field", "computed.ltv",
            "operator", "LESS_THAN_OR_EQUAL",
            "value", 0.9,
            "severity", "FAIL",
            "reason", "ltv rule");
    String criteriaBody =
        objectMapper.writeValueAsString(
            new CreateBankCriteriaVersionApiRequest(
                "v1", null, null, Map.of("rules", List.of(ltvRule))));
    mockMvc
        .perform(
            post("/api/v1/banks/" + bankId + "/criteria")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(criteriaBody))
        .andExpect(status().isOk());

    String matchResponse =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.globalResult").value("PASS"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(matchResponse).get("caseId").asText())
        .isEqualTo(caseId.toString());

    // 6. Bank contact + bank request
    String contactBody =
        objectMapper.writeValueAsString(
            new CreateBankContactApiRequest(
                bankId,
                "Contact E2E1",
                "Manager",
                null,
                null,
                "contact-e2e1@bank.test",
                null,
                null,
                null,
                "COMPANY"));
    String contactResponse =
        mockMvc
            .perform(
                post("/api/v1/bank-contacts")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(contactBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID contactId = UUID.fromString(objectMapper.readTree(contactResponse).get("id").asText());

    String bankRequestBody =
        objectMapper.writeValueAsString(new CreateBankRequestApiRequest(bankId, contactId));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/bank-requests")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(bankRequestBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caseId").value(caseId.toString()))
        .andExpect(jsonPath("$.bankId").value(bankId.toString()));
  }

  // ==================================================================================
  // Flow 2: casemgmt -> document -> crm -> portal
  // ==================================================================================

  @Test
  void documentPortalPublicationFlowEndToEnd() throws Exception {
    UUID companyId = companyRepository.insert("Co E2E2", "Co E2E2", "TC-E2E2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-e2e2");

    // 1. Case + client + case-client link
    UUID caseId = createCase(manager);
    String clientBody =
        objectMapper.writeValueAsString(
            new CreateClientApiRequest(
                "Portal",
                "Client",
                "portal-e2e2@client.test",
                "600000000",
                null,
                null,
                null,
                null,
                null,
                null));
    String clientResponse =
        mockMvc
            .perform(
                post("/api/v1/clients")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(clientBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID clientId = UUID.fromString(objectMapper.readTree(clientResponse).get("id").asText());

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/clients")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CaseClientApiRequest(clientId, "HOLDER", true))))
        .andExpect(status().isOk());

    // 2. Document: create -> upload -> review -> publish
    String documentBody =
        objectMapper.writeValueAsString(new CreateDocumentApiRequest(dniTypeId()));
    String documentResponse =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/documents")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(documentBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID documentId = UUID.fromString(objectMapper.readTree(documentResponse).get("id").asText());

    MockMultipartFile file =
        new MockMultipartFile("file", "dni.pdf", "application/pdf", "hello".getBytes());
    mockMvc
        .perform(
            multipart("/api/v1/documents/" + documentId + "/versions")
                .file(file)
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/review")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ReviewDocumentApiRequest("APPROVED", null))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/documents/" + documentId + "/publish")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    // 3. Portal account provisioning + portal read of the published document
    String portalExternalId = "portal-ext-e2e2-" + UUID.randomUUID();
    mockMvc
        .perform(
            post("/api/v1/clients/" + clientId + "/portal-account")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreatePortalAccountApiRequest(portalExternalId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    PortalPrincipal portal = new PortalPrincipal(portalExternalId);

    mockMvc
        .perform(
            get("/api/v1/portal/cases/" + caseId + "/documents")
                .header("Authorization", portal.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value(documentId.toString()));

    // Documented behavior (ADR-AUDIT-002): document lifecycle actions are all audited.
    List<AuditEvent> events =
        auditEventRepository.findAll().stream()
            .filter(e -> documentId.equals(e.resourceId()))
            .toList();
    assertThat(events).extracting(AuditEvent::action).contains("DOCUMENT_VERSION_UPLOADED");
    assertThat(events).extracting(AuditEvent::action).contains("DOCUMENT_REVIEWED");
  }

  // ==================================================================================
  // Flow 3: casemgmt -> scoring -> ai -> audit
  // ==================================================================================

  private static Map<String, Object> category(String name, Object maxScore) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("name", name);
    m.put("maxScore", maxScore);
    return m;
  }

  @Test
  void scoringAiAuditFlowEndToEnd() throws Exception {
    UUID companyId = companyRepository.insert("Co E2E3", "Co E2E3", "TC-E2E3");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-e2e3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-e2e3");

    // 1. Ruleset (SUPERADMIN, GLOBAL)
    String rulesetBody =
        objectMapper.writeValueAsString(
            Map.of(
                "code",
                "E2E3_RULESET",
                "version",
                "v1",
                "categories",
                List.of(category("LOW", 0), category("HIGH", null)),
                "rules",
                List.of(
                    Map.of(
                        "code", "term-long",
                        "weight", 10,
                        "field", "financingRequest.termMonths",
                        "operator", "GREATER_THAN_OR_EQUAL",
                        "value", 1))));
    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(rulesetBody))
        .andExpect(status().isOk());

    // 2. Case + scoring run
    UUID caseId = createCase(manager);
    String scoringResponse =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/scoring/run")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode results = objectMapper.readTree(scoringResponse);
    UUID resultId = UUID.fromString(results.get(0).get("id").asText());

    // 3. AI explanation (derived access: scoring result -> case)
    mockMvc
        .perform(
            post("/api/v1/scoring-results/" + resultId + "/ai/explanation")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AiUseCaseApiRequest("explain"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executed").value(false));

    // 4. Audit event verification
    AuditEvent event =
        auditEventRepository.findAll().stream()
            .filter(
                e ->
                    "AI_EXPLANATION_REQUESTED".equals(e.action())
                        && resultId.equals(e.resourceId()))
            .findFirst()
            .orElseThrow();
    assertThat(event.companyId()).isEqualTo(companyId);
    assertThat(event.actorUserId()).isEqualTo(manager.user().id());
    assertThat(event.resourceType()).isEqualTo("SCORING_RESULT");
  }
}
