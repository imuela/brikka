package com.brika.platform.identity.web;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
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
        .andExpect(jsonPath("$.permissions", hasSize(75))) // 71 (ADR-RBAC-001) + 1
        // (CLIENT_PORTAL_ACCOUNT_CREATE, ADR-PORTAL-AUTH-001, V11) + 2
        // (BANK_MATCHING_RUN/READ, ADR-BANKENGINE-001, V13) + 1
        // (BANK_MATCHING_OVERRIDE, ADR-BANKENGINE-002, V14)
        .andExpect(jsonPath("$.permissions", not(hasItem("AI_USE"))))
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
  void brokerCanReadUsersButNotCreate() throws Exception {
    UUID companyId = companyRepository.insert("Co BR1", "Co BR1", "TC-BR1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-br1");

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", broker.bearer()))
        .andExpect(status().isOk());

    String body =
        objectMapper.writeValueAsString(
            new CreateUserApiRequest(
                "new@brika.test", "New", "User", "BROKER", "ext-" + UUID.randomUUID()));
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
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-us1");

    mockMvc
        .perform(get("/api/v1/users").header("Authorization", superadmin.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCreatesUpdatesAndDisablesUserWithinOwnTenant() throws Exception {
    UUID companyId = companyRepository.insert("Co CRUD", "Co CRUD", "TC-CRUD1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-crud1");

    String createBody =
        objectMapper.writeValueAsString(
            new CreateUserApiRequest(
                "crud-target@brika.test", "Target", "User", "BROKER", "ext-" + UUID.randomUUID()));
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
}
