package com.brika.platform.financing.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseAssignmentApiRequest;
import com.brika.platform.financing.MortgagePaymentCalculator;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
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

  private String simulationBody(Map<String, Object> fields) throws Exception {
    return objectMapper.writeValueAsString(fields);
  }

  private JsonNode postSimulation(TestPrincipal actor, UUID caseId, Map<String, Object> fields)
      throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/simulations")
                    .header("Authorization", actor.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(simulationBody(fields)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response);
  }

  @Test
  void fixedSimulationAppliesBonificationsAndReturnsTheComputedPayment() throws Exception {
    UUID companyId = companyRepository.insert("Co SIM1", "Co SIM1", "TC-SIM1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-sim1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-sim1");
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);

    JsonNode created =
        postSimulation(
            broker,
            caseId,
            Map.of(
                "interestType",
                "FIXED",
                "principal",
                200000,
                "termMonths",
                300,
                "fixedRate",
                3.5,
                "bonifications",
                List.of(
                    Map.of("code", "PAYROLL", "rate", 0.30, "active", true),
                    Map.of("code", "HOME_INSURANCE", "rate", 0.10, "active", false)),
                "metadata",
                Map.of("note", "initial")));

    assertThat(created.get("interestType").asText()).isEqualTo("FIXED");
    assertThat(created.get("baseInterestRate").decimalValue()).isEqualByComparingTo("3.5000");
    // only the active PAYROLL bonification (0.30) applies -> 3.50 - 0.30 = 3.20
    assertThat(created.get("finalInterestRate").decimalValue()).isEqualByComparingTo("3.2000");
    assertThat(created.get("interestRate").decimalValue()).isEqualByComparingTo("3.2000");
    assertThat(created.get("estimatedPayment").decimalValue())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                new BigDecimal("200000"), new BigDecimal("3.2000"), 300));
    assertThat(created.get("variablePhase").isNull()).isTrue();
    assertThat(created.get("bonifications")).hasSize(2);
    assertThat(created.get("metadata").get("note").asText()).isEqualTo("initial");

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/simulations")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void variableSimulationUsesEuriborPlusSpreadMinusBonifications() throws Exception {
    UUID companyId = companyRepository.insert("Co SIMV", "Co SIMV", "TC-SIMV");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-simv");
    UUID caseId = createCase(manager);

    JsonNode created =
        postSimulation(
            manager,
            caseId,
            Map.of(
                "interestType",
                "VARIABLE",
                "principal",
                180000,
                "termMonths",
                360,
                "euriborRate",
                2.10,
                "spreadRate",
                0.99,
                "bonifications",
                List.of(Map.of("code", "PAYROLL", "rate", 0.50, "active", true))));

    assertThat(created.get("baseInterestRate").decimalValue()).isEqualByComparingTo("3.0900");
    assertThat(created.get("finalInterestRate").decimalValue()).isEqualByComparingTo("2.5900");
    assertThat(created.get("euriborRate").decimalValue()).isEqualByComparingTo("2.1000");
    assertThat(created.get("spreadRate").decimalValue()).isEqualByComparingTo("0.9900");
    assertThat(created.get("estimatedPayment").decimalValue())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                new BigDecimal("180000"), new BigDecimal("2.5900"), 360));
  }

  @Test
  void mixedSimulationReturnsBothTranches() throws Exception {
    UUID companyId = companyRepository.insert("Co SIMM", "Co SIMM", "TC-SIMM");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-simm");
    UUID caseId = createCase(manager);

    JsonNode created =
        postSimulation(
            manager,
            caseId,
            Map.of(
                "interestType",
                "MIXED",
                "principal",
                220000,
                "termMonths",
                360,
                "fixedPeriodMonths",
                120,
                "fixedPeriodRate",
                2.80,
                "euriborRate",
                2.00,
                "spreadRate",
                0.80,
                "bonifications",
                List.of(Map.of("code", "PAYROLL", "rate", 0.20, "active", true))));

    // fixed tranche: base 2.80, final 2.60, interest_rate = fixed-tranche final
    assertThat(created.get("baseInterestRate").decimalValue()).isEqualByComparingTo("2.8000");
    assertThat(created.get("finalInterestRate").decimalValue()).isEqualByComparingTo("2.6000");
    assertThat(created.get("interestRate").decimalValue()).isEqualByComparingTo("2.6000");
    assertThat(created.get("estimatedPayment").decimalValue())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                new BigDecimal("220000"), new BigDecimal("2.6000"), 360));

    JsonNode variablePhase = created.get("variablePhase");
    assertThat(variablePhase.isNull()).isFalse();
    // variable tranche: 2.00 + 0.80 = 2.80 base, - 0.20 = 2.60 final
    assertThat(variablePhase.get("baseInterestRate").decimalValue()).isEqualByComparingTo("2.8000");
    assertThat(variablePhase.get("finalInterestRate").decimalValue())
        .isEqualByComparingTo("2.6000");
    BigDecimal balance =
        MortgagePaymentCalculator.computeOutstandingBalance(
            new BigDecimal("220000"), new BigDecimal("2.6000"), 360, 120);
    assertThat(variablePhase.get("outstandingBalanceAtSwitch").decimalValue())
        .isEqualByComparingTo(balance);
    assertThat(variablePhase.get("monthlyPayment").decimalValue())
        .isEqualByComparingTo(
            MortgagePaymentCalculator.computeMonthlyPayment(
                balance, new BigDecimal("2.6000"), 240));
  }

  @Test
  void icoGuaranteeIsPersistedAndReturned() throws Exception {
    UUID companyId = companyRepository.insert("Co SIMI", "Co SIMI", "TC-SIMI");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-simi");
    UUID caseId = createCase(manager);

    JsonNode withIco =
        postSimulation(
            manager,
            caseId,
            Map.of(
                "interestType",
                "FIXED",
                "principal",
                90000,
                "termMonths",
                240,
                "fixedRate",
                3.0,
                "icoGuarantee",
                true));
    assertThat(withIco.get("icoGuarantee").asBoolean()).isTrue();

    JsonNode withoutIco =
        postSimulation(
            manager,
            caseId,
            Map.of(
                "interestType", "FIXED", "principal", 90000, "termMonths", 240, "fixedRate", 3.0));
    assertThat(withoutIco.get("icoGuarantee").asBoolean()).isFalse();

    String list =
        mockMvc
            .perform(
                get("/api/v1/cases/" + caseId + "/simulations")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(objectMapper.readTree(list)).hasSize(2);
  }

  @Test
  void invalidInterestModelIsRejectedWithAStructuredError() throws Exception {
    UUID companyId = companyRepository.insert("Co SIMX", "Co SIMX", "TC-SIMX");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-simx");
    UUID caseId = createCase(manager);

    // FIXED must not carry a Euribor rate.
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/simulations")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    simulationBody(
                        Map.of(
                            "interestType",
                            "FIXED",
                            "principal",
                            100000,
                            "termMonths",
                            240,
                            "fixedRate",
                            3.0,
                            "euriborRate",
                            2.0))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SIMULATION_INTEREST_MODEL_MISMATCH"))
        .andExpect(jsonPath("$.requestId").exists());

    // MIXED fixed period cannot cover the whole term.
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/simulations")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    simulationBody(
                        Map.of(
                            "interestType",
                            "MIXED",
                            "principal",
                            100000,
                            "termMonths",
                            240,
                            "fixedPeriodMonths",
                            240,
                            "fixedPeriodRate",
                            2.5,
                            "euriborRate",
                            2.0,
                            "spreadRate",
                            0.8))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SIMULATION_FIXED_PERIOD"));
  }

  @Test
  void brokerWithoutCaseAssignmentCannotCreateSimulation() throws Exception {
    UUID companyId = companyRepository.insert("Co SIM2", "Co SIM2", "TC-SIM2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-sim2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-sim2");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/simulations")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    simulationBody(
                        Map.of(
                            "interestType",
                            "FIXED",
                            "principal",
                            100000,
                            "termMonths",
                            240,
                            "fixedRate",
                            3.0))))
        .andExpect(status().isForbidden());
  }

  @Test
  void simulationFromAnotherTenantCaseIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co SIMTA", "Co SIMTA", "TC-SIMTA");
    UUID companyB = companyRepository.insert("Co SIMTB", "Co SIMTB", "TC-SIMTB");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-simta");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-simtb");
    UUID caseBId = createCase(managerB);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseBId + "/simulations")
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    simulationBody(
                        Map.of(
                            "interestType",
                            "FIXED",
                            "principal",
                            100000,
                            "termMonths",
                            240,
                            "fixedRate",
                            3.0))))
        .andExpect(status().isNotFound());
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
