package com.brika.platform.plan.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.brika.platform.plan.EntitlementRepository;
import com.brika.platform.plan.EntitlementValueType;
import com.brika.platform.plan.PlanEntitlementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Sprint 12.1 (ADR-PLATFORM-002): Plans CRUD, Company Subscriptions upsert/cancel, RBAC (SUPERADMIN
 * exclusive per 17_API_SPECIFICATION_DETAILED.md §4B), and D-MASTER-1 — the entitlement-gating
 * mechanism demonstrated end-to-end (subscription change -> GET /me reflects it) without gating any
 * real V1 feature by entitlement, since none is specified to need it.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class PlanSubscriptionEndpointsIT {

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
  @Autowired private EntitlementRepository entitlementRepository;
  @Autowired private PlanEntitlementRepository planEntitlementRepository;

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

  private UUID createPlan(TestPrincipal superadmin, String code) throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreatePlanApiRequest(code, "Plan " + code, "ACTIVE"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/plans")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  @Test
  void superadminCreatesReadsListsAndUpdatesPlans() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-pl1");
    UUID planId = createPlan(superadmin, "PLAN_PL1");

    mockMvc
        .perform(get("/api/v1/plans/" + planId).header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("PLAN_PL1"));

    mockMvc
        .perform(get("/api/v1/plans").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());

    String updateBody =
        objectMapper.writeValueAsString(new UpdatePlanApiRequest("Plan PL1 Renamed", "INACTIVE"));
    mockMvc
        .perform(
            patch("/api/v1/plans/" + planId)
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Plan PL1 Renamed"))
        .andExpect(jsonPath("$.status").value("INACTIVE"));
  }

  @Test
  void managerCannotAccessPlansOrSubscriptionEndpoints() throws Exception {
    UUID companyId = companyRepository.insert("Co PL2", "Co PL2", "TC-PL2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-pl2");

    mockMvc
        .perform(get("/api/v1/plans").header("Authorization", manager.bearer()))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            get("/api/v1/companies/" + companyId + "/subscription")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminUpsertsAndCancelsSubscription() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-su1");
    UUID companyId = companyRepository.insert("Co SUB1", "Co SUB1", "TC-SUB1");
    UUID planA = createPlan(superadmin, "PLAN_SUB1A");
    UUID planB = createPlan(superadmin, "PLAN_SUB1B");

    String createBody =
        objectMapper.writeValueAsString(new UpsertCompanySubscriptionApiRequest(planA, "ACTIVE"));
    mockMvc
        .perform(
            put("/api/v1/companies/" + companyId + "/subscription")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planId").value(planA.toString()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    String changeBody =
        objectMapper.writeValueAsString(new UpsertCompanySubscriptionApiRequest(planB, "TRIAL"));
    mockMvc
        .perform(
            put("/api/v1/companies/" + companyId + "/subscription")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(changeBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planId").value(planB.toString()))
        .andExpect(jsonPath("$.status").value("TRIAL"));

    mockMvc
        .perform(
            get("/api/v1/companies/" + companyId + "/subscription")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.planId").value(planB.toString()));

    mockMvc
        .perform(
            post("/api/v1/companies/" + companyId + "/subscription/cancel")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void upsertingASubscriptionWithAnUnknownPlanIsRejected() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-su2");
    UUID companyId = companyRepository.insert("Co SUB2", "Co SUB2", "TC-SUB2");

    String body =
        objectMapper.writeValueAsString(
            new UpsertCompanySubscriptionApiRequest(UUID.randomUUID(), "ACTIVE"));
    mockMvc
        .perform(
            put("/api/v1/companies/" + companyId + "/subscription")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PLAN_NOT_FOUND"));
  }

  /**
   * D-MASTER-1 (ADR-PLATFORM-002): no real V1 feature is gated by entitlement — this test
   * demonstrates the mechanism end-to-end through the newly wired write endpoints instead, exactly
   * as the ADR resolves it. Ad-hoc entitlement/plan data, same convention as PlanEntitlementIT.
   */
  @Test
  void subscriptionChangeViaApiIsReflectedInMeEntitlements() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-dm1");
    UUID companyId = companyRepository.insert("Co DM1", "Co DM1", "TC-DM1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dm1");

    UUID planFree = createPlan(superadmin, "PLAN_DM1_FREE");
    UUID planPro = createPlan(superadmin, "PLAN_DM1_PRO");
    UUID maxCasesId =
        entitlementRepository.insert(
            "DM1_MAX_CASES", "Max Cases", "Max concurrent cases", EntitlementValueType.NUMERIC);
    planEntitlementRepository.grant(planFree, maxCasesId, "5");
    planEntitlementRepository.grant(planPro, maxCasesId, "500");

    mockMvc
        .perform(
            put("/api/v1/companies/" + companyId + "/subscription")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpsertCompanySubscriptionApiRequest(planFree, "ACTIVE"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/me").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entitlements.DM1_MAX_CASES").value("5"));

    mockMvc
        .perform(
            put("/api/v1/companies/" + companyId + "/subscription")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new UpsertCompanySubscriptionApiRequest(planPro, "ACTIVE"))))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/me").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entitlements.DM1_MAX_CASES").value("500"));
  }
}
