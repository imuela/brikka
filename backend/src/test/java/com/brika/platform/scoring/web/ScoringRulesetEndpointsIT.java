package com.brika.platform.scoring.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * ADR-SCORING-001 D9-7: authoring/consultation of scoring_rulesets. SUPERADMIN-only write, GLOBAL
 * (no company_id, no SUPPORT_SESSION needed).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class ScoringRulesetEndpointsIT {

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

  private static Map<String, Object> category(String name, Object maxScore) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("name", name);
    m.put("maxScore", maxScore);
    return m;
  }

  private static Map<String, Object> rule(
      String code, Object weight, String field, String operator, Object value) {
    return Map.of(
        "code", code, "weight", weight, "field", field, "operator", operator, "value", value);
  }

  private String validRulesetBody(String code) throws Exception {
    return objectMapper.writeValueAsString(
        Map.of(
            "code",
            code,
            "version",
            "v1",
            "categories",
            List.of(category("LOW", 40), category("MEDIUM", 70), category("HIGH", null)),
            "rules",
            List.of(rule("ltv-ok", 20, "computed.ltv", "LESS_THAN_OR_EQUAL", 0.8))));
  }

  @Test
  void superadminCanCreateRuleset() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-sr1");

    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRulesetBody("PROPERTY_SCORE_SR1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PROPERTY_SCORE_SR1"))
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.rules", hasSize(1)));
  }

  @Test
  void managerCannotCreateRuleset() throws Exception {
    UUID companyId = companyRepository.insert("Co SR2", "Co SR2", "TC-SR2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-sr2");

    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRulesetBody("PROPERTY_SCORE_SR2")))
        .andExpect(status().isForbidden());
  }

  @Test
  void brokerCannotCreateRuleset() throws Exception {
    UUID companyId = companyRepository.insert("Co SR3", "Co SR3", "TC-SR3");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-sr3");

    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRulesetBody("PROPERTY_SCORE_SR3")))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotCreateRuleset() throws Exception {
    UUID companyId = companyRepository.insert("Co SR4", "Co SR4", "TC-SR4");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-sr4");

    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", client.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRulesetBody("PROPERTY_SCORE_SR4")))
        .andExpect(status().isForbidden());
  }

  @Test
  void getIsAccessibleToAllThreeInternalRoles() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-sr5");
    UUID companyId = companyRepository.insert("Co SR5", "Co SR5", "TC-SR5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-sr5");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-sr5");

    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRulesetBody("PROPERTY_SCORE_SR5")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/scoring/rulesets").header("Authorization", manager.bearer()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/scoring/rulesets").header("Authorization", broker.bearer()))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/scoring/rulesets").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void invalidCategoriesAreRejectedAndNeverPersisted() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-sr6");
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "code",
                "BAD_CATEGORIES_SR6",
                "version",
                "v1",
                "categories",
                List.of(category("ONLY_ONE", 40)), // missing the null catch-all
                "rules",
                List.of(rule("r1", 10, "computed.ltv", "LESS_THAN", 0.5))));

    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SCORING_CATEGORIES"));
  }

  @Test
  void unknownFieldIsRejectedAndNeverPersisted() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-sr7");
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "code",
                "BAD_FIELD_SR7",
                "version",
                "v1",
                "categories",
                List.of(category("LOW", null)),
                "rules",
                List.of(rule("r1", 10, "client.income", "EQUALS", 1))));

    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SCORING_RULES"));

    mockMvc
        .perform(get("/api/v1/scoring/rulesets").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("BAD_FIELD_SR7"))));
  }

  @Test
  void unknownOperatorIsRejected() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-sr8");
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "code",
                "BAD_OP_SR8",
                "version",
                "v1",
                "categories",
                List.of(category("LOW", null)),
                "rules",
                List.of(rule("r1", 10, "computed.ltv", "MATCHES_REGEX", 1))));

    mockMvc
        .perform(
            post("/api/v1/scoring/rulesets")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SCORING_RULES"));
  }
}
