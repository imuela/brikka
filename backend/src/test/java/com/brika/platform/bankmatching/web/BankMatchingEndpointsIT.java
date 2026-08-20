package com.brika.platform.bankmatching.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.bank.web.CreateBankApiRequest;
import com.brika.platform.bank.web.CreateBankCriteriaVersionApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseAssignmentApiRequest;
import com.brika.platform.financing.web.CreateFinancingRequestApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.brika.platform.property.web.UpsertPropertyApiRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
 * ADR-BANKENGINE-001 end-to-end tests: full matching flow, reproducibility, NO_ACTIVE_CRITERIA_
 * VERSION, invalid rules rejected at write time, tenant/case/permission isolation. No overrides, no
 * Sprint 6C, no Sprint 8 functionality is exercised here (none exists).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class BankMatchingEndpointsIT {

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

  private void upsertProperty(
      TestPrincipal principal, UUID caseId, BigDecimal valuation, BigDecimal purchasePrice)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new UpsertPropertyApiRequest(
                Map.of("city", "Madrid"), "FLAT", valuation, purchasePrice));
    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/property")
                .header("Authorization", principal.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private void createFinancingRequest(
      TestPrincipal principal, UUID caseId, BigDecimal amount, int termMonths) throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateFinancingRequestApiRequest(amount, termMonths));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financing-requests")
                .header("Authorization", principal.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private UUID createBank(TestPrincipal superadmin, String code) throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateBankApiRequest(code, "Bank " + code, Map.of()));
    String response =
        mockMvc
            .perform(
                post("/api/v1/banks")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private static Map<String, Object> ltvRule(
      String id, String operator, double value, String severity) {
    return Map.of(
        "id",
        id,
        "field",
        "computed.ltv",
        "operator",
        operator,
        "value",
        value,
        "severity",
        severity,
        "reason",
        "ltv rule");
  }

  private static Map<String, Object> termRule(
      String id, String operator, Object value, String severity) {
    return Map.of(
        "id",
        id,
        "field",
        "financingRequest.termMonths",
        "operator",
        operator,
        "value",
        value,
        "severity",
        severity,
        "reason",
        "term rule");
  }

  private void createActiveCriteria(
      TestPrincipal superadmin, UUID bankId, List<Map<String, Object>> rules) throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateBankCriteriaVersionApiRequest("v1", null, null, Map.of("rules", rules)));
    mockMvc
        .perform(
            post("/api/v1/banks/" + bankId + "/criteria")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void fullFlowProducesPassResultAndSnapshotIsReproducible() throws Exception {
    UUID companyId = companyRepository.insert("Co M1", "Co M1", "TC-M1");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-m1");
    UUID bankId = createBank(superadmin, "M1");
    createActiveCriteria(
        superadmin,
        bankId,
        List.of(
            ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL"),
            termRule("term-range", "BETWEEN", List.of(60, 360), "WARNING")));

    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("260000"), new BigDecimal("250000"));
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300);

    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.globalResult").value("PASS"))
            .andExpect(jsonPath("$.ruleResults", hasSize(2)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID resultId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    // Reproducibility: change the property after the fact, the stored snapshot must not change.
    upsertProperty(manager, caseId, new BigDecimal("400000"), new BigDecimal("400000"));

    mockMvc
        .perform(
            get("/api/v1/bank-match-results/" + resultId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.globalResult").value("PASS"))
        .andExpect(jsonPath("$.inputSnapshot.computed.ltv").value(0.76));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/matching").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void failResultWhenLtvExceedsThreshold() throws Exception {
    UUID companyId = companyRepository.insert("Co M2", "Co M2", "TC-M2");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-m2");
    UUID bankId = createBank(superadmin, "M2");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));

    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("200000"), new BigDecimal("200000"));
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300); // ltv = 0.95

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.globalResult").value("FAIL"))
        .andExpect(jsonPath("$.ruleResults[0].result").value("FAIL"));
  }

  @Test
  void notEvaluatedWhenPropertyIsMissing() throws Exception {
    UUID companyId = companyRepository.insert("Co M3", "Co M3", "TC-M3");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-m3");
    UUID bankId = createBank(superadmin, "M3");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));

    UUID caseId = createCase(manager);
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300); // no Property upserted

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.globalResult").value("NOT_EVALUATED"))
        .andExpect(jsonPath("$.ruleResults[0].result").value("NOT_EVALUATED"))
        .andExpect(jsonPath("$.ruleResults[0].evaluatedValue").value(nullValue()));
  }

  @Test
  void noActiveCriteriaVersionRejectsMatching() throws Exception {
    UUID companyId = companyRepository.insert("Co M4", "Co M4", "TC-M4");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-m4");
    UUID bankId = createBank(superadmin, "M4"); // no criteria version created at all
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NO_ACTIVE_CRITERIA_VERSION"));
  }

  @Test
  void invalidRulesAreRejectedAtCreationAndNeverPersisted() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m5");
    UUID bankId = createBank(superadmin, "M5");

    Map<String, Object> invalidRule =
        Map.of(
            "id",
            "bad-rule",
            "field",
            "client.income",
            "operator",
            "EQUALS",
            "value",
            1,
            "severity",
            "FAIL",
            "reason",
            "x");
    String body =
        objectMapper.writeValueAsString(
            new CreateBankCriteriaVersionApiRequest(
                "v1", null, null, Map.of("rules", List.of(invalidRule))));

    mockMvc
        .perform(
            post("/api/v1/banks/" + bankId + "/criteria")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CRITERIA_RULES"));

    mockMvc
        .perform(
            get("/api/v1/banks/" + bankId + "/criteria")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void brokerWithoutCaseAssignmentCannotRunMatching() throws Exception {
    UUID companyId = companyRepository.insert("Co M6", "Co M6", "TC-M6");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-m6");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-m6");
    UUID bankId = createBank(superadmin, "M6");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));
    UUID caseId = createCase(manager); // broker never assigned

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                .header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void matchingFromAnotherTenantCaseIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co M7A", "Co M7A", "TC-M7A");
    UUID companyB = companyRepository.insert("Co M7B", "Co M7B", "TC-M7B");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m7");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-m7a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-m7b");
    UUID bankId = createBank(superadmin, "M7");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));
    UUID caseBId = createCase(managerB);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseBId + "/banks/" + bankId + "/matching")
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void clientCannotRunOrReadMatching() throws Exception {
    UUID companyId = companyRepository.insert("Co M8", "Co M8", "TC-M8");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-m8");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-m8");
    UUID bankId = createBank(superadmin, "M8");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                .header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/matching").header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminWithoutSupportSessionCannotRunMatching() throws Exception {
    UUID companyId = companyRepository.insert("Co M9", "Co M9", "TC-M9");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m9");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-m9");
    UUID bankId = createBank(superadmin, "M9");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));
    UUID caseId = createCase(manager);

    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — matching is case-scoped and the tenant is
    // resolved from the case, so the endpoint is now accessible (200).
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void brokerWithCaseAssignmentCanRunMatching() throws Exception {
    UUID companyId = companyRepository.insert("Co M10", "Co M10", "TC-M10");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-m10");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-m10");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-m10");
    UUID bankId = createBank(superadmin, "M10");
    createActiveCriteria(
        superadmin,
        bankId,
        List.of(termRule("term-range", "BETWEEN", List.of(60, 360), "WARNING")));
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);
    createFinancingRequest(manager, caseId, new BigDecimal("100000"), 240);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                .header("Authorization", broker.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.globalResult").value("PASS"));
  }
}
