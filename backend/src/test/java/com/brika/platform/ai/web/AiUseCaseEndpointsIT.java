package com.brika.platform.ai.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.audit.AuditEvent;
import com.brika.platform.audit.AuditEventRepository;
import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseAssignmentApiRequest;
import com.brika.platform.communication.web.CreateConversationApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.JsonNode;
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
 * 21_AI_V1_SCOPE.md §2.B/C/D / Sprint 10 D10-1/D10-2/D10-3: the three synchronous AI use cases
 * (summarize/explain/draftMessage). No Worker involvement — NoOpAiProvider answers every call
 * honestly (executed=false, disclosed reason, never a fabricated output).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class AiUseCaseEndpointsIT {

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

  private String aiUseCaseBody(String context) throws Exception {
    return objectMapper.writeValueAsString(new AiUseCaseApiRequest(context));
  }

  // ---- Summary ----

  @Test
  void summaryExecutesHonestlyWithNoProvider() throws Exception {
    UUID companyId = companyRepository.insert("Co AU1", "Co AU1", "TC-AU1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-au1");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/ai/summary")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("case notes")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executed").value(false))
        .andExpect(jsonPath("$.output").doesNotExist())
        .andExpect(jsonPath("$.reason").isNotEmpty());

    AuditEvent event =
        auditEventRepository.findAll().stream()
            .filter(e -> "AI_SUMMARY_REQUESTED".equals(e.action()) && caseId.equals(e.resourceId()))
            .findFirst()
            .orElseThrow();
    assertThat(event.companyId()).isEqualTo(companyId);
    assertThat(event.actorUserId()).isEqualTo(manager.user().id());
    assertThat(event.resourceType()).isEqualTo("CASE");
  }

  @Test
  void brokerWithoutAssignmentCannotSummarize() throws Exception {
    UUID companyId = companyRepository.insert("Co AU2", "Co AU2", "TC-AU2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-au2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-au2");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/ai/summary")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("notes")))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotSummarize() throws Exception {
    UUID companyId = companyRepository.insert("Co AU3", "Co AU3", "TC-AU3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-au3");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-au3");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/ai/summary")
                .header("Authorization", client.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("notes")))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminWithoutSupportSessionCannotSummarize() throws Exception {
    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — AI use-cases are case-scoped and the tenant
    // is resolved from the case, so the endpoint is now accessible (200).
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-au4");
    UUID companyId = companyRepository.insert("Co AU4", "Co AU4", "TC-AU4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-au4");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/ai/summary")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("notes")))
        .andExpect(status().isOk());
  }

  @Test
  void summaryFromAnotherTenantCaseIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co AU5A", "Co AU5A", "TC-AU5A");
    UUID companyB = companyRepository.insert("Co AU5B", "Co AU5B", "TC-AU5B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-au5a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-au5b");
    UUID caseB = createCase(managerB);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseB + "/ai/summary")
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("notes")))
        .andExpect(status().isNotFound());
  }

  // ---- Explanation (derived access via ScoringResult -> Case) ----

  private static Map<String, Object> category(String name, Object maxScore) {
    Map<String, Object> m = new java.util.HashMap<>();
    m.put("name", name);
    m.put("maxScore", maxScore);
    return m;
  }

  private UUID createRuleset(TestPrincipal superadmin, String code) throws Exception {
    String body =
        objectMapper.writeValueAsString(
            Map.of(
                "code",
                code,
                "version",
                "v1",
                "categories",
                List.of(category("LOW", 0), category("HIGH", null)),
                "rules",
                List.of(
                    Map.of(
                        "code",
                        "term-long",
                        "weight",
                        10,
                        "field",
                        "financingRequest.termMonths",
                        "operator",
                        "GREATER_THAN_OR_EQUAL",
                        "value",
                        1))));
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

  private UUID runScoringAndGetFirstResultId(TestPrincipal principal, UUID caseId)
      throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/scoring/run")
                    .header("Authorization", principal.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode results = objectMapper.readTree(response);
    return UUID.fromString(results.get(0).get("id").asText());
  }

  @Test
  void explanationExecutesHonestlyWithNoProvider() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ae1");
    UUID companyId = companyRepository.insert("Co AE1", "Co AE1", "TC-AE1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ae1");
    createRuleset(superadmin, "AI_EXPLAIN_AE1");
    UUID caseId = createCase(manager);
    UUID resultId = runScoringAndGetFirstResultId(manager, caseId);

    mockMvc
        .perform(
            post("/api/v1/scoring-results/" + resultId + "/ai/explanation")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("explain this score")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executed").value(false))
        .andExpect(jsonPath("$.reason").isNotEmpty());

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

  @Test
  void explanationForUnknownResultIsNotFound() throws Exception {
    UUID companyId = companyRepository.insert("Co AE2", "Co AE2", "TC-AE2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ae2");

    mockMvc
        .perform(
            post("/api/v1/scoring-results/" + UUID.randomUUID() + "/ai/explanation")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("context")))
        .andExpect(status().isNotFound());
  }

  @Test
  void explanationFromAnotherTenantScoringResultIsNotFound() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ae3");
    UUID companyA = companyRepository.insert("Co AE3A", "Co AE3A", "TC-AE3A");
    UUID companyB = companyRepository.insert("Co AE3B", "Co AE3B", "TC-AE3B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-ae3a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-ae3b");
    createRuleset(superadmin, "AI_EXPLAIN_AE3");
    UUID caseB = createCase(managerB);
    UUID resultIdB = runScoringAndGetFirstResultId(managerB, caseB);

    mockMvc
        .perform(
            post("/api/v1/scoring-results/" + resultIdB + "/ai/explanation")
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("context")))
        .andExpect(status().isNotFound());
  }

  // ---- Draft message (derived access via Conversation -> Case; never auto-sent) ----

  private UUID createInternalConversation(TestPrincipal principal, UUID caseId) throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateConversationApiRequest("INTERNAL", null));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/conversations")
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
  void draftMessageExecutesHonestlyAndNeverAutoSends() throws Exception {
    UUID companyId = companyRepository.insert("Co AD1", "Co AD1", "TC-AD1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ad1");
    UUID caseId = createCase(manager);
    UUID conversationId = createInternalConversation(manager, caseId);

    mockMvc
        .perform(
            post("/api/v1/conversations/" + conversationId + "/ai/draft-message")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("draft a status update")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.executed").value(false))
        .andExpect(jsonPath("$.reason").isNotEmpty());

    mockMvc
        .perform(
            get("/api/v1/conversations/" + conversationId + "/messages")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(0));

    AuditEvent event =
        auditEventRepository.findAll().stream()
            .filter(
                e ->
                    "AI_DRAFT_MESSAGE_REQUESTED".equals(e.action())
                        && conversationId.equals(e.resourceId()))
            .findFirst()
            .orElseThrow();
    assertThat(event.companyId()).isEqualTo(companyId);
    assertThat(event.actorUserId()).isEqualTo(manager.user().id());
    assertThat(event.resourceType()).isEqualTo("CONVERSATION");
  }

  @Test
  void draftMessageForUnknownConversationIsNotFound() throws Exception {
    UUID companyId = companyRepository.insert("Co AD2", "Co AD2", "TC-AD2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ad2");

    mockMvc
        .perform(
            post("/api/v1/conversations/" + UUID.randomUUID() + "/ai/draft-message")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("context")))
        .andExpect(status().isNotFound());
  }

  @Test
  void draftMessageFromAnotherTenantConversationIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co AD3A", "Co AD3A", "TC-AD3A");
    UUID companyB = companyRepository.insert("Co AD3B", "Co AD3B", "TC-AD3B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-ad3a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-ad3b");
    UUID caseB = createCase(managerB);
    UUID conversationB = createInternalConversation(managerB, caseB);

    mockMvc
        .perform(
            post("/api/v1/conversations/" + conversationB + "/ai/draft-message")
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("context")))
        .andExpect(status().isNotFound());
  }

  @Test
  void brokerWithCaseAssignmentCanDraftMessage() throws Exception {
    UUID companyId = companyRepository.insert("Co AD4", "Co AD4", "TC-AD4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ad4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-ad4");
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);
    UUID conversationId = createInternalConversation(manager, caseId);

    mockMvc
        .perform(
            post("/api/v1/conversations/" + conversationId + "/ai/draft-message")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(aiUseCaseBody("context")))
        .andExpect(status().isOk());
  }
}
