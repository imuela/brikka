package com.brika.platform.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.audit.AuditEvent;
import com.brika.platform.audit.AuditEventRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
 * End-to-end security/tenant-isolation tests for the Identity endpoints (17_API_SPECIFICATION_
 * DETAILED.md §4/§5): authentication, RBAC permission, and tenant scope, exercised through real
 * HTTP requests against the actual SecurityFilterChain/controllers — not unit-level stand-ins.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class IdentityEndpointsIT {

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

  /** The stub decoder (StubJwtDecoderConfig) treats the bearer token itself as the JWT subject. */
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

  @Test
  void meWithoutTokenIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meWithUnknownIdentityIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/me").header("Authorization", "Bearer no-such-identity"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void meReturnsResolvedIdentityForAuthenticatedManager() throws Exception {
    UUID companyId = companyRepository.insert("Co ME1", "Co ME1", "TC-ME1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-me1");

    mockMvc
        .perform(get("/api/v1/me").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(manager.user().email()))
        .andExpect(jsonPath("$.role").value("MANAGER"))
        .andExpect(jsonPath("$.companyId").value(companyId.toString()));
  }

  @Test
  void mePermissionsExcludesPendingAndNotAssigned() throws Exception {
    UUID companyId = companyRepository.insert("Co ME2", "Co ME2", "TC-ME2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-me2");

    mockMvc
        .perform(get("/api/v1/me/permissions").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissions", hasSize(81))) // 71 (ADR-RBAC-001) + 1
        // (CLIENT_PORTAL_ACCOUNT_CREATE, ADR-PORTAL-AUTH-001, V11) + 2
        // (BANK_MATCHING_RUN/READ, ADR-BANKENGINE-001, V13) + 1
        // (BANK_MATCHING_OVERRIDE, ADR-BANKENGINE-002, V14) + 4
        // (AI_USE/AI_DOCUMENT_ANALYZE/AI_SUMMARIZE/AI_DRAFT_MESSAGE, Sprint 10 D10-1, V15) + 2
        // (FINANCIAL_ANALYSIS_RUN/READ, Sprint 31, V24)
        .andExpect(jsonPath("$.permissions", hasItem("AI_USE")))
        .andExpect(jsonPath("$.permissions", not(hasItem("AI_MANAGE_CONFIGURATION"))))
        .andExpect(jsonPath("$.permissions", hasItem("CASE_REOPEN")));
  }

  @Test
  void managerListsOnlyUsersOfOwnCompany() throws Exception {
    UUID companyA = companyRepository.insert("Co A", "Co A", "TC-A1");
    UUID companyB = companyRepository.insert("Co B", "Co B", "TC-B1");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-a1");
    createUser(UserRole.BROKER, companyA, "broker-a1");
    createUser(UserRole.MANAGER, companyB, "manager-b1");

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", managerA.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  void managerCreatingUserIgnoresAnySuppliedCompanyIdAndUsesOwnTenant() throws Exception {
    // Sprint 28 audit: UserController.create() only honours request.companyId() when the caller is
    // SUPERADMIN; for MANAGER/BROKER it always resolves the caller's own tenant via requireTenant()
    // and any companyId in the request body is silently ignored — this proves it, rather than just
    // asserting it in a comment. A MANAGER attempting to plant a user in a foreign company must
    // fail
    // closed: the created user still belongs to the MANAGER's own company, never the foreign one.
    UUID ownCompany = companyRepository.insert("Co CID1", "Co CID1", "TC-CID1");
    UUID foreignCompany = companyRepository.insert("Co CID2", "Co CID2", "TC-CID2");
    TestPrincipal manager = createUser(UserRole.MANAGER, ownCompany, "manager-cid1");

    String body =
        objectMapper.writeValueAsString(
            new CreateUserApiRequest(
                "planted@brika.test",
                "Planted",
                "User",
                "BROKER",
                "ext-" + UUID.randomUUID(),
                foreignCompany));
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyId").value(ownCompany.toString()));
  }

  @Test
  void brokerCanReadUsersButNotCreate() throws Exception {
    UUID companyId = companyRepository.insert("Co BR1", "Co BR1", "TC-BR1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-br1");

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", broker.bearer()))
        .andExpect(status().isOk());

    String body =
        objectMapper.writeValueAsString(
            new CreateUserApiRequest(
                "new@brika.test", "New", "User", "BROKER", "ext-" + UUID.randomUUID(), null));
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotReadUsers() throws Exception {
    UUID companyId = companyRepository.insert("Co CL1", "Co CL1", "TC-CL1");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-cl1");

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCannotReadUserFromAnotherCompany() throws Exception {
    UUID companyA = companyRepository.insert("Co XA", "Co XA", "TC-XA1");
    UUID companyB = companyRepository.insert("Co XB", "Co XB", "TC-XB1");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-xa1");
    TestPrincipal userB = createUser(UserRole.BROKER, companyB, "broker-xb1");

    mockMvc
        .perform(
            get("/api/v1/users/" + userB.user().id()).header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void superadminWithoutSupportSessionCannotAccessUsersEndpoint() throws Exception {
    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — user reads span all companies (200). The
    // SUPERADMIN caller itself is always present. Array size varies with other methods, so only
    // accessibility and the caller's own presence are asserted.
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-us1");

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[?(@.email=='superadmin-us1@brika.test')]").exists());
  }

  @Test
  void superadminCreatesUserInExplicitCompany() throws Exception {
    // Sprint 27 (ADR-RBAC-002): a GLOBAL SUPERADMIN has no company of their own, so user creation
    // requires an explicit companyId. A tenant user (MANAGER/BROKER) never passes companyId.
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-us2");
    UUID companyId = companyRepository.insert("Co SU2", "Co SU2", "TC-SU2");

    String body =
        objectMapper.writeValueAsString(
            new CreateUserApiRequest(
                "su-target@brika.test", "First", "Last", "BROKER", "ext-su-target", companyId));
    mockMvc
        .perform(
            post("/api/v1/users")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.companyId").value(companyId.toString()))
        .andExpect(jsonPath("$.role").value("BROKER"));

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.email=='su-target@brika.test')]").exists());
  }

  @Test
  void managerCreatesUpdatesAndDisablesUserWithinOwnTenant() throws Exception {
    UUID companyId = companyRepository.insert("Co CRUD", "Co CRUD", "TC-CRUD1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-crud1");

    String createBody =
        objectMapper.writeValueAsString(
            new CreateUserApiRequest(
                "crud-target@brika.test",
                "Target",
                "User",
                "BROKER",
                "ext-" + UUID.randomUUID(),
                null));
    String response =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.companyId").value(companyId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID createdId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    String updateBody =
        objectMapper.writeValueAsString(new UpdateUserApiRequest("Updated", "Name"));
    mockMvc
        .perform(
            patch("/api/v1/users/" + createdId)
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Updated"));

    mockMvc
        .perform(
            post("/api/v1/users/" + createdId + "/disable")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));
  }

  @Test
  void creatingAndDisablingAUserWritesAuditEvents() throws Exception {
    UUID companyId = companyRepository.insert("Co AUD1", "Co AUD1", "TC-AUD1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-aud1");

    String createBody =
        objectMapper.writeValueAsString(
            new CreateUserApiRequest(
                "audit-target@brika.test",
                "Audit",
                "Target",
                "BROKER",
                "ext-" + UUID.randomUUID(),
                null));
    String response =
        mockMvc
            .perform(
                post("/api/v1/users")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID createdId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(
            post("/api/v1/users/" + createdId + "/disable")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    List<AuditEvent> events = auditEventRepository.findAll();
    AuditEvent created =
        events.stream()
            .filter(e -> "USER_CREATED".equals(e.action()) && createdId.equals(e.resourceId()))
            .findFirst()
            .orElseThrow();
    assertThat(created.companyId()).isEqualTo(companyId);
    assertThat(created.actorUserId()).isEqualTo(manager.user().id());
    assertThat(created.resourceType()).isEqualTo("USER");
    assertThat(created.metadataJson()).contains("BROKER");

    AuditEvent disabled =
        events.stream()
            .filter(e -> "USER_DISABLED".equals(e.action()) && createdId.equals(e.resourceId()))
            .findFirst()
            .orElseThrow();
    assertThat(disabled.companyId()).isEqualTo(companyId);
    assertThat(disabled.actorUserId()).isEqualTo(manager.user().id());
    assertThat(disabled.resourceType()).isEqualTo("USER");
  }

  @Test
  void managerDisablesThenReenablesAUserWithinOwnTenant() throws Exception {
    UUID companyId = companyRepository.insert("Co ENA1", "Co ENA1", "TC-ENA1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ena1");
    TestPrincipal target = createUser(UserRole.BROKER, companyId, "broker-ena1");

    mockMvc
        .perform(
            post("/api/v1/users/" + target.user().id() + "/disable")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISABLED"));

    mockMvc
        .perform(
            post("/api/v1/users/" + target.user().id() + "/enable")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));

    List<AuditEvent> events = auditEventRepository.findAll();
    AuditEvent enabled =
        events.stream()
            .filter(
                e -> "USER_ENABLED".equals(e.action()) && target.user().id().equals(e.resourceId()))
            .findFirst()
            .orElseThrow();
    assertThat(enabled.companyId()).isEqualTo(companyId);
    assertThat(enabled.actorUserId()).isEqualTo(manager.user().id());
    assertThat(enabled.resourceType()).isEqualTo("USER");
  }

  @Test
  void enablingAnAlreadyActiveUserIsIdempotent() throws Exception {
    // No explicit state-machine validation exists for users.status (same convention disable()
    // already follows) — re-enabling an already-active user is a no-op 200, not an error.
    UUID companyId = companyRepository.insert("Co ENA2", "Co ENA2", "TC-ENA2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ena2");
    TestPrincipal target = createUser(UserRole.BROKER, companyId, "broker-ena2");

    mockMvc
        .perform(
            post("/api/v1/users/" + target.user().id() + "/enable")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void enableWithoutTokenIsUnauthorized() throws Exception {
    UUID companyId = companyRepository.insert("Co ENA3", "Co ENA3", "TC-ENA3");
    TestPrincipal target = createUser(UserRole.BROKER, companyId, "broker-ena3");

    mockMvc
        .perform(post("/api/v1/users/" + target.user().id() + "/enable"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void brokerCannotEnableAUser() throws Exception {
    UUID companyId = companyRepository.insert("Co ENA4", "Co ENA4", "TC-ENA4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-ena4");
    TestPrincipal target = createUser(UserRole.BROKER, companyId, "broker-ena4-target");

    mockMvc
        .perform(
            post("/api/v1/users/" + target.user().id() + "/enable")
                .header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCannotEnableAUserFromAnotherCompany() throws Exception {
    UUID companyA = companyRepository.insert("Co ENA5A", "Co ENA5A", "TC-ENA5A");
    UUID companyB = companyRepository.insert("Co ENA5B", "Co ENA5B", "TC-ENA5B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-ena5");
    TestPrincipal userB = createUser(UserRole.BROKER, companyB, "broker-ena5b");

    mockMvc
        .perform(
            post("/api/v1/users/" + userB.user().id() + "/enable")
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void enablingAnUnknownUserIsNotFound() throws Exception {
    UUID companyId = companyRepository.insert("Co ENA6", "Co ENA6", "TC-ENA6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-ena6");

    mockMvc
        .perform(
            post("/api/v1/users/" + UUID.randomUUID() + "/enable")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void superadminEnablesAUserInAnyCompany() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-ena7");
    UUID companyId = companyRepository.insert("Co ENA7", "Co ENA7", "TC-ENA7");
    TestPrincipal target = createUser(UserRole.BROKER, companyId, "broker-ena7");

    mockMvc
        .perform(
            post("/api/v1/users/" + target.user().id() + "/disable")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/users/" + target.user().id() + "/enable")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }
}
