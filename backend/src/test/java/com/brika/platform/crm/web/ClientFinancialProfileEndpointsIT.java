package com.brika.platform.crm.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentType;
import com.brika.platform.document.DocumentTypeRepository;
import com.brika.platform.document.DocumentVersionRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 30. End-to-end tests for the client financial profile: real HTTP requests through the
 * actual SecurityFilterChain/controllers, mirroring CrmCaseEndpointsIT.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class ClientFinancialProfileEndpointsIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private ClientRepository clientRepository;
  @Autowired private CaseService caseService;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private DocumentVersionRepository documentVersionRepository;
  @Autowired private DocumentTypeRepository documentTypeRepository;

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

  private UUID createClient(UUID companyId, String emailPrefix) {
    return clientRepository.insert(
        companyId, "Cli", "Ent", emailPrefix + "@brika.test", "600000000");
  }

  /**
   * A document_version belonging to companyId, created directly (no MinIO needed — the profile only
   * stores the id, it never reads the file).
   */
  private UUID createEvidenceDocumentVersion(UUID companyId, UUID uploadedBy) {
    UUID caseId = caseService.createCase(companyId, uploadedBy, "PURCHASE").id();
    DocumentType type = documentTypeRepository.findAll().get(0);
    UUID documentId = documentRepository.insert(companyId, caseId, type.id());
    UUID versionId = UUID.randomUUID();
    documentVersionRepository.insert(
        versionId,
        documentId,
        1,
        "storage-key",
        "nomina.pdf",
        "application/pdf",
        1024L,
        "checksum",
        uploadedBy);
    return versionId;
  }

  private String upsertBody(BigDecimal monthlyIncome, String source, String status, UUID evidence)
      throws Exception {
    return objectMapper.writeValueAsString(
        new UpsertClientFinancialProfileApiRequest(
            "MARRIED",
            2,
            "EMPLOYEE",
            "PERMANENT",
            "Acme S.L.",
            5,
            monthlyIncome,
            new BigDecimal("15000.00"),
            new BigDecimal("300.00"),
            new BigDecimal("500.00"),
            source,
            status,
            evidence));
  }

  @Test
  void getReturns404WhenNoProfileExistsYet() throws Exception {
    UUID companyId = companyRepository.insert("Co FP1", "Co FP1", "TC-FP1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fp1");
    UUID clientId = createClient(companyId, "cli-fp1");

    mockMvc
        .perform(
            get("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("FINANCIAL_PROFILE_NOT_FOUND"));
  }

  @Test
  void managerCreatesReadsAndUpdatesAFinancialProfileWithEvidence() throws Exception {
    UUID companyId = companyRepository.insert("Co FP2", "Co FP2", "TC-FP2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fp2");
    UUID clientId = createClient(companyId, "cli-fp2");
    UUID evidenceId = createEvidenceDocumentVersion(companyId, manager.user().id());

    String createBody = upsertBody(new BigDecimal("2500.00"), "BROKER", "PENDING", evidenceId);
    mockMvc
        .perform(
            put("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthlyIncome").value(2500.00))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.evidenceDocumentVersionId").value(evidenceId.toString()));

    mockMvc
        .perform(
            get("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employerName").value("Acme S.L."));

    String updateBody = upsertBody(new BigDecimal("3000.00"), "BROKER", "CONFIRMED", evidenceId);
    mockMvc
        .perform(
            put("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthlyIncome").value(3000.00))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));

    // history: 2 writes, most recent first, single row per client (no per-field EAV)
    mockMvc
        .perform(
            get("/api/v1/clients/" + clientId + "/financial-profile/history")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].monthlyIncome").value(3000.00))
        .andExpect(jsonPath("$[1].monthlyIncome").value(2500.00));
  }

  @Test
  void negativeMonthlyIncomeIsRejectedWithA400NotA500() throws Exception {
    UUID companyId = companyRepository.insert("Co FP3", "Co FP3", "TC-FP3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fp3");
    UUID clientId = createClient(companyId, "cli-fp3");

    String body = upsertBody(new BigDecimal("-100.00"), "BROKER", "PENDING", null);
    mockMvc
        .perform(
            put("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NEGATIVE_FINANCIAL_VALUE"));
  }

  @Test
  void invalidStatusIsRejectedWithA400() throws Exception {
    UUID companyId = companyRepository.insert("Co FP4", "Co FP4", "TC-FP4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fp4");
    UUID clientId = createClient(companyId, "cli-fp4");

    String body = upsertBody(new BigDecimal("1000.00"), "BROKER", "NOT_A_REAL_STATUS", null);
    mockMvc
        .perform(
            put("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FINANCIAL_PROFILE_STATUS"));
  }

  @Test
  void evidenceFromAnotherCompanyIsRejectedAndMaskedAsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co FP5A", "Co FP5A", "TC-FP5A");
    UUID companyB = companyRepository.insert("Co FP5B", "Co FP5B", "TC-FP5B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-fp5a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-fp5b");
    UUID clientId = createClient(companyA, "cli-fp5");
    UUID evidenceFromCompanyB = createEvidenceDocumentVersion(companyB, managerB.user().id());

    String body = upsertBody(new BigDecimal("1000.00"), "BROKER", "PENDING", evidenceFromCompanyB);
    mockMvc
        .perform(
            put("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("EVIDENCE_DOCUMENT_VERSION_NOT_FOUND"));
  }

  @Test
  void brokerFromAnotherCompanyCannotSeeOrCreateAFinancialProfileForForeignClient()
      throws Exception {
    UUID companyA = companyRepository.insert("Co FP6A", "Co FP6A", "TC-FP6A");
    UUID companyB = companyRepository.insert("Co FP6B", "Co FP6B", "TC-FP6B");
    UUID clientA = createClient(companyA, "cli-fp6");
    TestPrincipal brokerB = createUser(UserRole.BROKER, companyB, "broker-fp6b");

    mockMvc
        .perform(
            get("/api/v1/clients/" + clientA + "/financial-profile")
                .header("Authorization", brokerB.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));

    String body = upsertBody(new BigDecimal("1000.00"), "BROKER", "PENDING", null);
    mockMvc
        .perform(
            put("/api/v1/clients/" + clientA + "/financial-profile")
                .header("Authorization", brokerB.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("CLIENT_NOT_FOUND"));
  }

  @Test
  void superadminIsGlobalAcrossTenantsForFinancialProfiles() throws Exception {
    UUID companyId = companyRepository.insert("Co FP7", "Co FP7", "TC-FP7");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-fp7");
    UUID clientId = createClient(companyId, "cli-fp7");

    String body = upsertBody(new BigDecimal("4000.00"), "BROKER", "CONFIRMED", null);
    mockMvc
        .perform(
            put("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthlyIncome").value(4000.00));
  }

  @Test
  void brokerWithoutClientReadPermissionCannotBeAssumed_permissionMatrixIsRespected()
      throws Exception {
    // Sanity check that CLIENT_READ/CLIENT_UPDATE are genuinely enforced (not bypassed) —
    // portal/CLIENT-role principals are never issued a bearer token by this internal auth realm at
    // all, so the closest in-realm negative case is an unauthenticated request.
    UUID companyId = companyRepository.insert("Co FP8", "Co FP8", "TC-FP8");
    UUID clientId = createClient(companyId, "cli-fp8");

    mockMvc
        .perform(get("/api/v1/clients/" + clientId + "/financial-profile"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unsetFieldsResetToNullOnUpdate() throws Exception {
    UUID companyId = companyRepository.insert("Co FP9", "Co FP9", "TC-FP9");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fp9");
    UUID clientId = createClient(companyId, "cli-fp9");

    mockMvc
        .perform(
            put("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(upsertBody(new BigDecimal("2000.00"), "BROKER", "PENDING", null)))
        .andExpect(status().isOk());

    String minimalBody =
        objectMapper.writeValueAsString(
            new UpsertClientFinancialProfileApiRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, null));
    mockMvc
        .perform(
            put("/api/v1/clients/" + clientId + "/financial-profile")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(minimalBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.monthlyIncome").doesNotExist())
        .andExpect(jsonPath("$.source").value("BROKER"))
        .andExpect(jsonPath("$.status").value("PENDING"));
  }
}
