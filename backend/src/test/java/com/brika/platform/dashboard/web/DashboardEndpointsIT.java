package com.brika.platform.dashboard.web;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
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
 * Sprint 27, Bloque 2: role-aware dashboard (FUNCTIONAL_SPECIFICATION.md §3). Validates that a
 * MANAGER sees company-wide metrics, a BROKER only assigned metrics, a GLOBAL SUPERADMIN sees all
 * companies, and CLIENT (no ACTIVITY_READ) is denied.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class DashboardEndpointsIT {

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

  private void assignBroker(UUID caseId, TestPrincipal assigning, TestPrincipal broker)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new com.brika.platform.casemgmt.web.CreateCaseAssignmentApiRequest(
                broker.user().id(), "BROKER"));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/assignments")
                .header("Authorization", assigning.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void unauthenticatedDashboardIsUnauthorized() throws Exception {
    mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
  }

  @Test
  void managerSeesCompanyWideDashboardMetrics() throws Exception {
    UUID companyId = companyRepository.insert("Co DB1", "Co DB1", "TC-DB1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-db1");
    createCase(manager);

    mockMvc
        .perform(get("/api/v1/dashboard").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCases").value(1))
        .andExpect(jsonPath("$.casesByStatus.PRESTUDY").value(1))
        .andExpect(jsonPath("$.pendingTasks").value(0))
        .andExpect(jsonPath("$.overdueTasks").value(0))
        .andExpect(jsonPath("$.pendingDocumentRequests").value(0))
        .andExpect(jsonPath("$.recentActivity").isArray());
  }

  @Test
  void superadminSeesGlobalDashboardAcrossCompanies() throws Exception {
    UUID companyA = companyRepository.insert("Co DA", "Co DA", "TC-DA");
    UUID companyB = companyRepository.insert("Co DB", "Co DB", "TC-DB");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-da");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-db");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-dash");
    createCase(managerA);
    createCase(managerB);

    mockMvc
        .perform(get("/api/v1/dashboard").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCases", greaterThan(1)))
        .andExpect(jsonPath("$.recentActivity").isArray())
        .andExpect(jsonPath("$.recentActivity").isNotEmpty());
  }

  @Test
  void brokerDashboardIsScopedToAssignedCases() throws Exception {
    UUID companyId = companyRepository.insert("Co DC", "Co DC", "TC-DC");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-dc");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-dc");
    UUID caseAssigned = createCase(manager);
    createCase(manager);
    assignBroker(caseAssigned, manager, broker);

    mockMvc
        .perform(get("/api/v1/dashboard").header("Authorization", broker.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeCases").value(1))
        .andExpect(jsonPath("$.casesByStatus").value(notNullValue()));
  }

  @Test
  void clientWithoutActivityReadIsDenied() throws Exception {
    UUID companyId = companyRepository.insert("Co DD", "Co DD", "TC-DD");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-dd");

    mockMvc
        .perform(get("/api/v1/dashboard").header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }
}
