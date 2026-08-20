package com.brika.platform.bankmatching.web;

import static org.hamcrest.Matchers.hasSize;
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
import com.fasterxml.jackson.databind.JsonNode;
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
 * ADR-BANKENGINE-002 end-to-end tests: overrides of a single bank_match_rule_result, effective
 * result/global derivation, optimistic concurrency (409), no-op rejection (400), RBAC (MANAGER/
 * SUPERADMIN-with-SUPPORT_SESSION only, never BROKER/CLIENT), tenant isolation, and immutability of
 * the original bank_match_results/bank_match_rule_results rows. No Sprint 6C Activity, no Portal
 * exposure, no new operators — none of that exists here.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class BankMatchOverrideEndpointsIT {

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
        "id", id,
        "field", "computed.ltv",
        "operator", operator,
        "value", value,
        "severity", severity,
        "reason", "ltv rule");
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

  private String runMatching(TestPrincipal principal, UUID caseId, UUID bankId) throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/banks/" + bankId + "/matching")
                .header("Authorization", principal.bearer()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private UUID ruleResultId(String matchResponse, int index) throws Exception {
    return UUID.fromString(
        objectMapper.readTree(matchResponse).get("ruleResults").get(index).get("id").asText());
  }

  /**
   * Looks up a rule result by its rule id rather than array position — insertion order across rules
   * within one match run is not a guaranteed ordering (same-transaction inserts can share
   * created_at), so tests that care about a specific rule must not rely on array index.
   */
  private UUID ruleResultIdByRuleId(String matchResponse, String ruleId) throws Exception {
    for (JsonNode node : objectMapper.readTree(matchResponse).get("ruleResults")) {
      if (ruleId.equals(node.get("ruleId").asText())) {
        return UUID.fromString(node.get("id").asText());
      }
    }
    throw new AssertionError("No rule result found for ruleId " + ruleId);
  }

  private String overrideBody(String previousResult, String newResult, String reason)
      throws Exception {
    return objectMapper.writeValueAsString(
        new CreateBankMatchRuleOverrideApiRequest(previousResult, newResult, reason));
  }

  /** One-rule fixture: FAIL if ltv > 0.80. Property/financing set up to trigger FAIL. */
  private record Fixture(
      TestPrincipal superadmin, TestPrincipal manager, UUID caseId, UUID bankId) {}

  private Fixture failingSingleRuleFixture(String suffix) throws Exception {
    UUID companyId = companyRepository.insert("Co O" + suffix, "Co O" + suffix, "TC-O" + suffix);
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-o" + suffix);
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-o" + suffix);
    UUID bankId = createBank(superadmin, "O" + suffix);
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));
    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("200000"), new BigDecimal("200000"));
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300); // ltv = 0.95 -> FAIL
    return new Fixture(superadmin, manager, caseId, bankId);
  }

  @Test
  void overrideFailToPassMakesEffectiveResultPassWithoutTouchingTheOriginal() throws Exception {
    Fixture fx = failingSingleRuleFixture("1");
    String matchResponse = runMatching(fx.manager(), fx.caseId(), fx.bankId());
    UUID resultId = UUID.fromString(objectMapper.readTree(matchResponse).get("id").asText());
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", fx.manager().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "PASS", "Broker confirmed extra guarantee")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previousResult").value("FAIL"))
        .andExpect(jsonPath("$.newResult").value("PASS"));

    mockMvc
        .perform(
            get("/api/v1/bank-match-results/" + resultId)
                .header("Authorization", fx.manager().bearer()))
        .andExpect(status().isOk())
        // Original rows remain completely intact.
        .andExpect(jsonPath("$.globalResult").value("FAIL"))
        .andExpect(jsonPath("$.ruleResults[0].result").value("FAIL"))
        // Effective values reflect the override.
        .andExpect(jsonPath("$.effectiveGlobalResult").value("PASS"))
        .andExpect(jsonPath("$.ruleResults[0].effectiveResult").value("PASS"))
        .andExpect(jsonPath("$.ruleResults[0].overrideCount").value(1))
        .andExpect(jsonPath("$.ruleResults[0].overrides", hasSize(1)))
        .andExpect(jsonPath("$.ruleResults[0].overrides[0].previousResult").value("FAIL"))
        .andExpect(jsonPath("$.ruleResults[0].overrides[0].newResult").value("PASS"));
  }

  @Test
  void overridePassToFailMakesEffectiveGlobalFail() throws Exception {
    UUID companyId = companyRepository.insert("Co O2", "Co O2", "TC-O2");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-o2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-o2");
    UUID bankId = createBank(superadmin, "O2");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));
    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("260000"), new BigDecimal("250000"));
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300); // ltv = 0.76 -> PASS

    String matchResponse = runMatching(manager, caseId, bankId);
    UUID resultId = UUID.fromString(objectMapper.readTree(matchResponse).get("id").asText());
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("PASS", "FAIL", "Manager flagged undisclosed risk")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/bank-match-results/" + resultId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.globalResult").value("PASS")) // original untouched
        .andExpect(jsonPath("$.effectiveGlobalResult").value("FAIL"));
  }

  @Test
  void overrideNotEvaluatedToPass() throws Exception {
    UUID companyId = companyRepository.insert("Co O3", "Co O3", "TC-O3");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-o3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-o3");
    UUID bankId = createBank(superadmin, "O3");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));
    UUID caseId = createCase(manager);
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300); // no Property

    String matchResponse = runMatching(manager, caseId, bankId);
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("NOT_EVALUATED", "PASS", "Property confirmed off-system")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.previousResult").value("NOT_EVALUATED"))
        .andExpect(jsonPath("$.newResult").value("PASS"));
  }

  @Test
  void secondOverrideOnSameRuleWinsAndHistoryIsPreserved() throws Exception {
    Fixture fx = failingSingleRuleFixture("4");
    String matchResponse = runMatching(fx.manager(), fx.caseId(), fx.bankId());
    UUID resultId = UUID.fromString(objectMapper.readTree(matchResponse).get("id").asText());
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", fx.manager().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "WARNING", "First correction")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", fx.manager().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("WARNING", "PASS", "Second correction, guarantee confirmed")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/bank-match-results/" + resultId)
                .header("Authorization", fx.manager().bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleResults[0].result").value("FAIL")) // original still intact
        .andExpect(jsonPath("$.ruleResults[0].effectiveResult").value("PASS")) // most recent wins
        .andExpect(jsonPath("$.ruleResults[0].overrideCount").value(2))
        .andExpect(jsonPath("$.ruleResults[0].overrides", hasSize(2)))
        .andExpect(jsonPath("$.ruleResults[0].overrides[0].newResult").value("WARNING"))
        .andExpect(jsonPath("$.ruleResults[0].overrides[1].newResult").value("PASS"));
  }

  @Test
  void stalePreviousResultIsRejectedWithConflict() throws Exception {
    Fixture fx = failingSingleRuleFixture("5");
    String matchResponse = runMatching(fx.manager(), fx.caseId(), fx.bankId());
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", fx.manager().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "WARNING", "First correction")))
        .andExpect(status().isOk());

    // Effective is now WARNING, but this request still claims the stale FAIL.
    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", fx.manager().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "PASS", "Stale attempt")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("OVERRIDE_STALE_PREVIOUS_RESULT"));

    mockMvc
        .perform(
            get("/api/v1/bank-match-results/"
                    + UUID.fromString(objectMapper.readTree(matchResponse).get("id").asText()))
                .header("Authorization", fx.manager().bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleResults[0].overrideCount").value(1)); // no override inserted
  }

  @Test
  void noOpOverrideIsRejected() throws Exception {
    Fixture fx = failingSingleRuleFixture("6");
    String matchResponse = runMatching(fx.manager(), fx.caseId(), fx.bankId());
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", fx.manager().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "FAIL", "No actual change")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("OVERRIDE_NOOP"));
  }

  @Test
  void brokerCanNeverOverrideEvenWithCaseAssignment() throws Exception {
    UUID companyId = companyRepository.insert("Co O7", "Co O7", "TC-O7");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-o7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-o7");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-o7");
    UUID bankId = createBank(superadmin, "O7");
    createActiveCriteria(
        superadmin, bankId, List.of(ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL")));
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId); // even assigned, still no permission
    upsertProperty(manager, caseId, new BigDecimal("200000"), new BigDecimal("200000"));
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300);

    String matchResponse = runMatching(manager, caseId, bankId);
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "PASS", "Broker attempt")))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCanNeverOverride() throws Exception {
    Fixture fx = failingSingleRuleFixture("8");
    TestPrincipal client =
        createUser(UserRole.CLIENT, fx.manager().user().companyId(), "client-o8");
    String matchResponse = runMatching(fx.manager(), fx.caseId(), fx.bankId());
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", client.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "PASS", "Client attempt")))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminWithoutSupportSessionCannotOverride() throws Exception {
    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — overrides target case-scoped rule results
    // whose tenant is resolved from the case, so the endpoint is now accessible (200).
    Fixture fx = failingSingleRuleFixture("9");
    String matchResponse = runMatching(fx.manager(), fx.caseId(), fx.bankId());
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", fx.superadmin().bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "PASS", "Superadmin attempt")))
        .andExpect(status().isOk());
  }

  @Test
  void overridingAnotherTenantsRuleResultIsNotFound() throws Exception {
    Fixture fx = failingSingleRuleFixture("10a");
    String matchResponse = runMatching(fx.manager(), fx.caseId(), fx.bankId());
    UUID ruleResultId = ruleResultId(matchResponse, 0);

    UUID companyB = companyRepository.insert("Co O10B", "Co O10B", "TC-O10B");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-o10b");

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + ruleResultId + "/overrides")
                .header("Authorization", managerB.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "PASS", "Cross-tenant attempt")))
        .andExpect(status().isNotFound());
  }

  @Test
  void unknownRuleResultIsNotFound() throws Exception {
    UUID companyId = companyRepository.insert("Co O11", "Co O11", "TC-O11");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-o11");

    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + UUID.randomUUID() + "/overrides")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "PASS", "Unknown target")))
        .andExpect(status().isNotFound());
  }

  @Test
  void effectiveGlobalResultIsRecalculatedAcrossMultipleRules() throws Exception {
    UUID companyId = companyRepository.insert("Co O12", "Co O12", "TC-O12");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-o12");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-o12");
    UUID bankId = createBank(superadmin, "O12");
    createActiveCriteria(
        superadmin,
        bankId,
        List.of(
            ltvRule("max-ltv-80", "LESS_THAN_OR_EQUAL", 0.80, "FAIL"),
            ltvRule("min-ltv-10", "GREATER_THAN_OR_EQUAL", 0.10, "WARNING")));
    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("200000"), new BigDecimal("200000"));
    createFinancingRequest(
        manager,
        caseId,
        new BigDecimal("190000"),
        300); // ltv=0.95: FAIL, WARNING would PASS (>=0.10)

    String matchResponse = runMatching(manager, caseId, bankId);
    UUID resultId = UUID.fromString(objectMapper.readTree(matchResponse).get("id").asText());
    UUID failingRuleResultId = ruleResultIdByRuleId(matchResponse, "max-ltv-80");

    // Global was FAIL (FAIL beats WARNING/PASS). Override the FAIL rule to PASS: since the other
    // rule already PASSes, the effective global should now become PASS too.
    mockMvc
        .perform(
            post("/api/v1/bank-match-rule-results/" + failingRuleResultId + "/overrides")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(overrideBody("FAIL", "PASS", "Guarantee confirmed")))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/bank-match-results/" + resultId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.globalResult").value("FAIL"))
        .andExpect(jsonPath("$.effectiveGlobalResult").value("PASS"));
  }
}
