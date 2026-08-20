package com.brika.platform.financing.web;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseAssignmentApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
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
 * End-to-end security/tenant/CASE ASSIGNMENT tests for Simulation and FinancingRequest — Sprint 5.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class FinancingEndpointsIT {

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

  @Test
  void simulationCreateAndListForAssignedBroker() throws Exception {
    UUID companyId = companyRepository.insert("Co SIM1", "Co SIM1", "TC-SIM1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-sim1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-sim1");
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);

    String body =
        objectMapper.writeValueAsString(
            new CreateSimulationApiRequest(
                new BigDecimal("200000"),
                new BigDecimal("3.5"),
                300,
                new BigDecimal("950.25"),
                Map.of("note", "initial")));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/simulations")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.principal").value(200000))
        .andExpect(jsonPath("$.metadata.note").value("initial"));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/simulations")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void brokerWithoutCaseAssignmentCannotCreateSimulation() throws Exception {
    UUID companyId = companyRepository.insert("Co SIM2", "Co SIM2", "TC-SIM2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-sim2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-sim2");
    UUID caseId = createCase(manager);

    String body =
        objectMapper.writeValueAsString(
            new CreateSimulationApiRequest(
                new BigDecimal("100000"),
                new BigDecimal("3.0"),
                240,
                new BigDecimal("550.00"),
                Map.of()));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/simulations")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotAccessSimulations() throws Exception {
    UUID companyId = companyRepository.insert("Co SIM3", "Co SIM3", "TC-SIM3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-sim3");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-sim3");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/simulations")
                .header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void financingRequestCreateListAndStandalonePatch() throws Exception {
    UUID companyId = companyRepository.insert("Co FR1", "Co FR1", "TC-FR1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fr1");
    UUID caseId = createCase(manager);

    String createBody =
        objectMapper.writeValueAsString(
            new CreateFinancingRequestApiRequest(new BigDecimal("150000"), 300));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/financing-requests")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/financing-requests")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    String patchBody =
        objectMapper.writeValueAsString(
            new UpdateFinancingRequestApiRequest("IN_PROGRESS", new BigDecimal("160000"), 300));
    mockMvc
        .perform(
            patch("/api/v1/financing-requests/" + id)
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.requestedAmount").value(160000));
  }

  @Test
  void financingRequestFromAnotherTenantIsNotFoundOnStandalonePatch() throws Exception {
    UUID companyA = companyRepository.insert("Co FR2A", "Co FR2A", "TC-FR2A");
    UUID companyB = companyRepository.insert("Co FR2B", "Co FR2B", "TC-FR2B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-fr2a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-fr2b");
    UUID caseBId = createCase(managerB);

    String createBody =
        objectMapper.writeValueAsString(
            new CreateFinancingRequestApiRequest(new BigDecimal("50000"), 120));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseBId + "/financing-requests")
                    .header("Authorization", managerB.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID id = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    String patchBody =
        objectMapper.writeValueAsString(
            new UpdateFinancingRequestApiRequest("CLOSED", new BigDecimal("50000"), 120));
    mockMvc
        .perform(
            patch("/api/v1/financing-requests/" + id)
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isNotFound());
  }

  @Test
  void superadminWithoutSupportSessionCannotAccessFinancingRequests() throws Exception {
    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — financing requests are case-scoped and the
    // tenant is resolved from the case, so the endpoint is now accessible (200, empty list).
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-fr3");
    UUID companyId = companyRepository.insert("Co FR3", "Co FR3", "TC-FR3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fr3");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/financing-requests")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }
}
