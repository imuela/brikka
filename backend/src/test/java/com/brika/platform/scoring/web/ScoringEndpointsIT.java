package com.brika.platform.scoring.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * ADR-SCORING-001 (D9-1 through D9-7) end-to-end tests: PROPERTY/OPERATION score calculation,
 * TRIGGERED/NOT_TRIGGERED/NOT_EVALUATED outcomes, negative weights, category resolution, multiple
 * active rulesets, reproducibility, RBAC, tenant/CASE ASSIGNMENT isolation. scoring_rulesets is
 * GLOBAL (D9-6) — run evaluates every ACTIVE ruleset regardless of who created it, so tests locate
 * their own result by rulesetId rather than assuming an isolated result list.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class ScoringEndpointsIT {

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

  private static Map<String, Object> category(String name, Object maxScore) {
    java.util.Map<String, Object> m = new java.util.HashMap<>();
    m.put("name", name);
    m.put("maxScore", maxScore);
    return m;
  }

  private static Map<String, Object> rule(
      String code, Object weight, String field, String operator, Object value) {
    return Map.of(
        "code", code, "weight", weight, "field", field, "operator", operator, "value", value);
  }

  private UUID createRuleset(
      TestPrincipal superadmin,
      String code,
      List<Map<String, Object>> categories,
      List<Map<String, Object>> rules)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of("code", code, "version", "v1", "categories", categories, "rules", rules));
    String response =
        mockMvc
            .perform(
                post("/api/v1/scoring/rulesets")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private JsonNode runScoring(TestPrincipal principal, UUID caseId) throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/scoring/run")
                    .header("Authorization", principal.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response);
  }

  private JsonNode resultFor(JsonNode results, UUID rulesetId) {
    for (JsonNode result : results) {
      if (rulesetId.toString().equals(result.get("rulesetId").asText())) {
        return result;
      }
    }
    throw new AssertionError("No scoring result found for ruleset " + rulesetId);
  }

  @Test
  void propertyScoreTriggersCorrectlyAndResolvesCategory() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se1");
    UUID companyId = companyRepository.insert("Co SE1", "Co SE1", "TC-SE1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se1");

    UUID rulesetId =
        createRuleset(
            superadmin,
            "PROPERTY_SCORE_SE1",
            List.of(category("LOW", 20), category("HIGH", null)),
            List.of(rule("ltv-ok", 30, "computed.ltv", "LESS_THAN_OR_EQUAL", 0.8)));

    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("260000"), new BigDecimal("250000"));
    createFinancingRequest(
        manager, caseId, new BigDecimal("190000"), 300); // ltv = 0.76 <= 0.8 -> TRIGGERED

    JsonNode results = runScoring(manager, caseId);
    JsonNode result = resultFor(results, rulesetId);
    org.assertj.core.api.Assertions.assertThat(result.get("totalScore").decimalValue())
        .isEqualByComparingTo("30");
    org.assertj.core.api.Assertions.assertThat(result.get("category").asText()).isEqualTo("HIGH");
    JsonNode ruleExplanation = result.get("explanation").get("rules").get(0);
    org.assertj.core.api.Assertions.assertThat(ruleExplanation.get("outcome").asText())
        .isEqualTo("TRIGGERED");
  }

  @Test
  void operationScoreUsesOnlyTermAndAmount() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se2");
    UUID companyId = companyRepository.insert("Co SE2", "Co SE2", "TC-SE2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se2");

    UUID rulesetId =
        createRuleset(
            superadmin,
            "OPERATION_SCORE_SE2",
            List.of(category("LOW", 0), category("HIGH", null)),
            List.of(
                rule("term-long", 10, "financingRequest.termMonths", "GREATER_THAN_OR_EQUAL", 240),
                rule(
                    "amount-high", 5, "financingRequest.requestedAmount", "GREATER_THAN", 300000)));

    UUID caseId = createCase(manager);
    createFinancingRequest(
        manager, caseId, new BigDecimal("190000"), 300); // term triggers, amount does not

    JsonNode results = runScoring(manager, caseId);
    JsonNode result = resultFor(results, rulesetId);
    org.assertj.core.api.Assertions.assertThat(result.get("totalScore").decimalValue())
        .isEqualByComparingTo("10");
  }

  @Test
  void negativeWeightReducesTotalScore() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se3");
    UUID companyId = companyRepository.insert("Co SE3", "Co SE3", "TC-SE3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se3");

    UUID rulesetId =
        createRuleset(
            superadmin,
            "PROPERTY_SCORE_SE3",
            List.of(category("LOW", -100), category("HIGH", null)),
            List.of(rule("high-ltv-penalty", -15, "computed.ltv", "GREATER_THAN", 0.7)));

    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("200000"), new BigDecimal("200000"));
    createFinancingRequest(
        manager, caseId, new BigDecimal("190000"), 300); // ltv = 0.95 > 0.7 -> TRIGGERED

    JsonNode results = runScoring(manager, caseId);
    JsonNode result = resultFor(results, rulesetId);
    org.assertj.core.api.Assertions.assertThat(result.get("totalScore").decimalValue())
        .isEqualByComparingTo("-15");
  }

  @Test
  void ruleIsNotEvaluatedWhenPropertyIsMissing() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se4");
    UUID companyId = companyRepository.insert("Co SE4", "Co SE4", "TC-SE4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se4");

    UUID rulesetId =
        createRuleset(
            superadmin,
            "PROPERTY_SCORE_SE4",
            List.of(category("LOW", 0), category("HIGH", null)),
            List.of(rule("ltv-ok", 30, "computed.ltv", "LESS_THAN_OR_EQUAL", 0.8)));

    UUID caseId = createCase(manager);
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300); // no Property

    JsonNode results = runScoring(manager, caseId);
    JsonNode result = resultFor(results, rulesetId);
    org.assertj.core.api.Assertions.assertThat(result.get("totalScore").decimalValue())
        .isEqualByComparingTo("0");
    JsonNode ruleExplanation = result.get("explanation").get("rules").get(0);
    org.assertj.core.api.Assertions.assertThat(ruleExplanation.get("outcome").asText())
        .isEqualTo("NOT_EVALUATED");
    org.assertj.core.api.Assertions.assertThat(ruleExplanation.get("evaluatedValue").isNull())
        .isTrue();
  }

  @Test
  void multipleActiveRulesetsEachProduceTheirOwnResult() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se5");
    UUID companyId = companyRepository.insert("Co SE5", "Co SE5", "TC-SE5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se5");

    UUID propertyRulesetId =
        createRuleset(
            superadmin,
            "PROPERTY_SCORE_SE5",
            List.of(category("LOW", 0), category("HIGH", null)),
            List.of(rule("ltv-ok", 20, "computed.ltv", "LESS_THAN_OR_EQUAL", 0.8)));
    UUID operationRulesetId =
        createRuleset(
            superadmin,
            "OPERATION_SCORE_SE5",
            List.of(category("LOW", 0), category("HIGH", null)),
            List.of(
                rule(
                    "term-long", 10, "financingRequest.termMonths", "GREATER_THAN_OR_EQUAL", 240)));

    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("260000"), new BigDecimal("250000"));
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300);

    JsonNode results = runScoring(manager, caseId);
    resultFor(results, propertyRulesetId); // throws if absent
    resultFor(results, operationRulesetId); // throws if absent
  }

  @Test
  void ltvFallsBackToSingleDenominatorWhenPurchasePriceMissing() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se6");
    UUID companyId = companyRepository.insert("Co SE6", "Co SE6", "TC-SE6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se6");

    UUID rulesetId =
        createRuleset(
            superadmin,
            "PROPERTY_SCORE_SE6",
            List.of(category("LOW", 0), category("HIGH", null)),
            List.of(rule("ltv-ok", 30, "computed.ltv", "LESS_THAN_OR_EQUAL", 0.8)));

    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("200000"), null); // only valuation
    createFinancingRequest(
        manager, caseId, new BigDecimal("190000"), 300); // ltv = 190000/200000 = 0.95

    JsonNode results = runScoring(manager, caseId);
    JsonNode result = resultFor(results, rulesetId);
    org.assertj.core.api.Assertions.assertThat(
            result.get("explanation").get("snapshot").get("ltv").decimalValue())
        .isEqualByComparingTo("0.9500");
  }

  @Test
  void brokerWithoutCaseAssignmentCannotRunScoring() throws Exception {
    UUID companyId = companyRepository.insert("Co SE7", "Co SE7", "TC-SE7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se7");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-se7");
    UUID caseId = createCase(manager); // broker never assigned

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/scoring/run")
                .header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void brokerWithCaseAssignmentCanRunScoring() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se8");
    UUID companyId = companyRepository.insert("Co SE8", "Co SE8", "TC-SE8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se8");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-se8");

    createRuleset(
        superadmin,
        "PROPERTY_SCORE_SE8",
        List.of(category("LOW", 0), category("HIGH", null)),
        List.of(rule("ltv-ok", 10, "computed.ltv", "LESS_THAN_OR_EQUAL", 0.8)));

    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);
    upsertProperty(manager, caseId, new BigDecimal("260000"), new BigDecimal("250000"));
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/scoring/run")
                .header("Authorization", broker.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void superadminWithoutSupportSessionCannotRunOrReadScoring() throws Exception {
    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — scoring is case-scoped and the tenant is
    // resolved from the case, so the endpoints are now accessible (200).
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se9");
    UUID companyId = companyRepository.insert("Co SE9", "Co SE9", "TC-SE9");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se9");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/scoring/run")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/scoring/results")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void clientCannotRunOrReadScoring() throws Exception {
    UUID companyId = companyRepository.insert("Co SE10", "Co SE10", "TC-SE10");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se10");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-se10");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/scoring/run")
                .header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/scoring/results")
                .header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void scoringFromAnotherTenantCaseIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co SE11A", "Co SE11A", "TC-SE11A");
    UUID companyB = companyRepository.insert("Co SE11B", "Co SE11B", "TC-SE11B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-se11a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-se11b");
    UUID caseBId = createCase(managerB);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseBId + "/scoring/run")
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(
            get("/api/v1/cases/" + caseBId + "/scoring/results")
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void scoringResultIsReproducibleAfterPropertyIsChanged() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-se12");
    UUID companyId = companyRepository.insert("Co SE12", "Co SE12", "TC-SE12");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se12");

    UUID rulesetId =
        createRuleset(
            superadmin,
            "PROPERTY_SCORE_SE12",
            List.of(category("LOW", 0), category("HIGH", null)),
            List.of(rule("ltv-ok", 30, "computed.ltv", "LESS_THAN_OR_EQUAL", 0.8)));

    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("260000"), new BigDecimal("250000"));
    createFinancingRequest(manager, caseId, new BigDecimal("190000"), 300);

    JsonNode firstRun = runScoring(manager, caseId);
    JsonNode firstResult = resultFor(firstRun, rulesetId);
    BigDecimal originalScore = firstResult.get("totalScore").decimalValue();
    BigDecimal originalLtv =
        firstResult.get("explanation").get("snapshot").get("ltv").decimalValue();

    // Reproducibility: change the property after the fact, the stored result must not change.
    upsertProperty(manager, caseId, new BigDecimal("400000"), new BigDecimal("400000"));

    String response =
        mockMvc
            .perform(
                get("/api/v1/cases/" + caseId + "/scoring/results")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode storedResult = resultFor(objectMapper.readTree(response), rulesetId);

    org.assertj.core.api.Assertions.assertThat(storedResult.get("totalScore").decimalValue())
        .isEqualByComparingTo(originalScore);
    org.assertj.core.api.Assertions.assertThat(
            storedResult.get("explanation").get("snapshot").get("ltv").decimalValue())
        .isEqualByComparingTo(originalLtv);
  }

  /**
   * BRIKKA V2 I2: the case RAG indicator (GET /scoring/rag) is readable by the case team via the
   * existing SCORING_READ permission and its scoring axis reflects the V29 factory ruleset without
   * anyone authoring one.
   */
  @Test
  void ragIndicatorIsReadableByTheCaseTeamAndReflectsTheFactoryScoring() throws Exception {
    UUID companyId = companyRepository.insert("Co SE13", "Co SE13", "TC-SE13");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-se13");

    UUID caseId = createCase(manager);
    upsertProperty(manager, caseId, new BigDecimal("300000"), new BigDecimal("300000"));
    createFinancingRequest(manager, caseId, new BigDecimal("180000"), 300);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/scoring/run")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    String response =
        mockMvc
            .perform(
                get("/api/v1/cases/" + caseId + "/scoring/rag")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode rag = objectMapper.readTree(response);

    // LTV 180000/300000 = 0.60 -> ltv-strong+ltv-moderate+term-standard+amount-known = 100 ->
    // GREEN.
    org.assertj.core.api.Assertions.assertThat(rag.get("rag").asText()).isEqualTo("GREEN");
    org.assertj.core.api.Assertions.assertThat(rag.get("axes").size()).isEqualTo(3);
    JsonNode scoringAxis = null;
    for (JsonNode candidate : rag.get("axes")) {
      if ("scoring".equals(candidate.get("axis").asText())) {
        scoringAxis = candidate;
      }
    }
    org.assertj.core.api.Assertions.assertThat(scoringAxis).isNotNull();
    org.assertj.core.api.Assertions.assertThat(scoringAxis.get("level").asText())
        .isEqualTo("GREEN");
  }

  @Test
  void ragFromAnotherTenantCaseIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co SE14A", "Co SE14A", "TC-SE14A");
    UUID companyB = companyRepository.insert("Co SE14B", "Co SE14B", "TC-SE14B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-se14a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-se14b");
    UUID caseBId = createCase(managerB);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseBId + "/scoring/rag")
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }
}
