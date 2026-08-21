package com.brika.platform.financialanalysis.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.bank.BankRepository;
import com.brika.platform.bankrequest.BankOfferRepository;
import com.brika.platform.bankrequest.BankRequestRepository;
import com.brika.platform.bankrequest.FinalFinancingRepository;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.crm.ClientFinancialProfileService;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.financing.SimulationRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 31. End-to-end tests for the financial viability analysis: real HTTP requests through the
 * actual SecurityFilterChain/controllers, mirroring ScoringEndpointsIT/BankMatchingEndpointsIT/
 * ClientFinancialProfileEndpointsIT.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class FinancialAnalysisEndpointsIT {

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
  @Autowired private ClientRepository clientRepository;
  @Autowired private ClientFinancialProfileService financialProfileService;
  @Autowired private CaseService caseService;
  @Autowired private CaseClientRepository caseClientRepository;
  @Autowired private SimulationRepository simulationRepository;
  @Autowired private BankRepository bankRepository;
  @Autowired private BankRequestRepository bankRequestRepository;
  @Autowired private BankOfferRepository bankOfferRepository;
  @Autowired private FinalFinancingRepository finalFinancingRepository;

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

  private UUID createClientWithProfile(
      UUID companyId,
      String emailPrefix,
      TestPrincipal actor,
      BigDecimal monthlyIncome,
      BigDecimal debts) {
    UUID clientId =
        clientRepository.insert(companyId, "Cli", "Ent", emailPrefix + "@brika.test", "600000000");
    financialProfileService.upsert(
        companyId,
        clientId,
        null,
        null,
        null,
        null,
        null,
        null,
        monthlyIncome,
        null,
        debts,
        null,
        "BROKER",
        "CONFIRMED",
        null,
        actor.user().id());
    return clientId;
  }

  private UUID createCaseWithSimulation(
      UUID companyId, TestPrincipal actor, BigDecimal principal, BigDecimal rate, int term) {
    UUID caseId = caseService.createCase(companyId, actor.user().id(), "PURCHASE").id();
    // estimated_payment is NOT NULL but is a broker-declared placeholder, never read by
    // FinancialAnalysisService (which always recomputes its own payment) — any value works here.
    simulationRepository.insert(
        companyId, caseId, principal, rate, term, BigDecimal.ZERO, "{}", actor.user().id());
    return caseId;
  }

  @Test
  void managerRunsAnAnalysisUsingTheMostRecentSimulationAndReadsItBack() throws Exception {
    UUID companyId = companyRepository.insert("Co FA1", "Co FA1", "TC-FA1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa1");
    UUID caseId =
        createCaseWithSimulation(
            companyId, manager, new BigDecimal("200000"), new BigDecimal("3.5"), 360);
    UUID clientId =
        createClientWithProfile(
            companyId, "cli-fa1", manager, new BigDecimal("3000"), new BigDecimal("300"));
    caseClientRepository.insert(caseId, clientId, ParticipationType.HOLDER, true);

    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/financial-analysis")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].quotaSource").value("SIMULATION"))
            .andExpect(jsonPath("$[0].monthlyPayment").value(898.09))
            .andExpect(jsonPath("$[0].dtiPercent").value(39.94)) // (300+898.09)/3000*100
            .andExpect(jsonPath("$[0].viabilityCategory").value("REVISAR"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode explanation = objectMapper.readTree(response).get(0).get("explanation");
    assertThat(explanation.get("disclaimer").asText()).contains("orientativa interna");

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void selectedBankOfferTakesPriorityOverASimulation() throws Exception {
    UUID companyId = companyRepository.insert("Co FA2", "Co FA2", "TC-FA2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa2");
    UUID caseId =
        createCaseWithSimulation(
            companyId, manager, new BigDecimal("100000"), new BigDecimal("5"), 120);
    UUID clientId =
        createClientWithProfile(
            companyId, "cli-fa2", manager, new BigDecimal("4000"), BigDecimal.ZERO);
    caseClientRepository.insert(caseId, clientId, ParticipationType.HOLDER, true);

    UUID bankId = bankRepository.insert("BK-FA2", "Bank FA2", null);
    UUID bankRequestId = bankRequestRepository.insert(companyId, caseId, bankId, null, "{}");
    UUID bankOfferId =
        bankOfferRepository.insert(
            companyId,
            bankRequestId,
            bankId,
            new BigDecimal("150000"),
            new BigDecimal("2"),
            240,
            new BigDecimal("999"),
            "{}");
    finalFinancingRepository.insert(companyId, caseId, bankOfferId);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].quotaSource").value("BANK_OFFER"))
        .andExpect(jsonPath("$[0].principal").value(150000.00));
  }

  @Test
  void everyLinkedClientGetsItsOwnResultSharingTheSamePayment() throws Exception {
    UUID companyId = companyRepository.insert("Co FA3", "Co FA3", "TC-FA3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa3");
    UUID caseId =
        createCaseWithSimulation(
            companyId, manager, new BigDecimal("100000"), new BigDecimal("3"), 240);
    UUID holderId =
        createClientWithProfile(
            companyId, "cli-fa3-h", manager, new BigDecimal("2000"), BigDecimal.ZERO);
    UUID coHolderId =
        createClientWithProfile(
            companyId, "cli-fa3-c", manager, new BigDecimal("1500"), new BigDecimal("100"));
    caseClientRepository.insert(caseId, holderId, ParticipationType.HOLDER, true);
    caseClientRepository.insert(caseId, coHolderId, ParticipationType.CO_HOLDER, false);

    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/financial-analysis")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode results = objectMapper.readTree(response);
    BigDecimal payment0 = results.get(0).get("monthlyPayment").decimalValue();
    BigDecimal payment1 = results.get(1).get("monthlyPayment").decimalValue();
    assertThat(payment0).isEqualByComparingTo(payment1); // same mortgage, same payment
    assertThat(results.get(0).get("clientId").asText())
        .isNotEqualTo(results.get(1).get("clientId").asText());
  }

  @Test
  void missingFinancialProfileFailsFastWithAStructured400NotA500() throws Exception {
    UUID companyId = companyRepository.insert("Co FA4", "Co FA4", "TC-FA4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa4");
    UUID caseId =
        createCaseWithSimulation(
            companyId, manager, new BigDecimal("100000"), new BigDecimal("3"), 240);
    UUID clientId =
        clientRepository.insert(companyId, "Cli", "Ent", "cli-fa4@brika.test", "600000000");
    caseClientRepository.insert(caseId, clientId, ParticipationType.HOLDER, true);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FINANCIAL_PROFILE_REQUIRED"));
  }

  @Test
  void noClientsOnCaseIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co FA5", "Co FA5", "TC-FA5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa5");
    UUID caseId =
        createCaseWithSimulation(
            companyId, manager, new BigDecimal("100000"), new BigDecimal("3"), 240);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NO_CLIENTS_ON_CASE"));
  }

  @Test
  void noFinancingDataIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co FA6", "Co FA6", "TC-FA6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa6");
    UUID caseId = caseService.createCase(companyId, manager.user().id(), "PURCHASE").id();
    UUID clientId =
        createClientWithProfile(
            companyId, "cli-fa6", manager, new BigDecimal("2000"), BigDecimal.ZERO);
    caseClientRepository.insert(caseId, clientId, ParticipationType.HOLDER, true);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("FINANCING_DATA_REQUIRED"));
  }

  @Test
  void brokerNotAssignedToTheCaseIsForbidden() throws Exception {
    UUID companyId = companyRepository.insert("Co FA7", "Co FA7", "TC-FA7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa7");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-fa7");
    UUID caseId =
        createCaseWithSimulation(
            companyId, manager, new BigDecimal("100000"), new BigDecimal("3"), 240);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminIsGlobalAcrossTenants() throws Exception {
    UUID companyId = companyRepository.insert("Co FA8", "Co FA8", "TC-FA8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa8");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-fa8");
    UUID caseId =
        createCaseWithSimulation(
            companyId, manager, new BigDecimal("100000"), new BigDecimal("3"), 240);
    UUID clientId =
        createClientWithProfile(
            companyId, "cli-fa8", manager, new BigDecimal("2000"), BigDecimal.ZERO);
    caseClientRepository.insert(caseId, clientId, ParticipationType.HOLDER, true);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  @Test
  void managerFromAnotherCompanyGetsAMaskedNotFoundNotForbidden() throws Exception {
    UUID companyA = companyRepository.insert("Co FA9A", "Co FA9A", "TC-FA9A");
    UUID companyB = companyRepository.insert("Co FA9B", "Co FA9B", "TC-FA9B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-fa9a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-fa9b");
    UUID caseId =
        createCaseWithSimulation(
            companyA, managerA, new BigDecimal("100000"), new BigDecimal("3"), 240);

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/financial-analysis")
                .header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co FA10", "Co FA10", "TC-FA10");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-fa10");
    UUID caseId =
        createCaseWithSimulation(
            companyId, manager, new BigDecimal("100000"), new BigDecimal("3"), 240);

    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/financial-analysis"))
        .andExpect(status().isUnauthorized());
  }
}
