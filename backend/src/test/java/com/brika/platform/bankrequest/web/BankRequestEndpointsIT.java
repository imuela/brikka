package com.brika.platform.bankrequest.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.activity.Activity;
import com.brika.platform.activity.ActivityRepository;
import com.brika.platform.bank.web.CreateBankApiRequest;
import com.brika.platform.bank.web.CreateBankContactApiRequest;
import com.brika.platform.bank.web.UpdateBankContactApiRequest;
import com.brika.platform.bankrequest.FinalFinancing;
import com.brika.platform.bankrequest.FinalFinancingRepository;
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
import java.util.Optional;
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
 * End-to-end security/tenant/CASE ASSIGNMENT tests for Sprint 6A: BankRequest, BankResponse,
 * BankOffer, FinalFinancing. Matching engine and overrides are explicitly out of scope (D1/D2) and
 * therefore have no tests here.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class BankRequestEndpointsIT {

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
  @Autowired private ActivityRepository activityRepository;
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

  private UUID createBank(TestPrincipal superadmin, String code) throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateBankApiRequest(code, "Bank " + code, Map.of()));
    String response =
        mockMvc
            .perform(
                post("/api/v1/banks")
                    .header("Authorization", superadmin.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private UUID createBankContact(TestPrincipal principal, UUID bankId, String name)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateBankContactApiRequest(
                bankId,
                name,
                "Manager",
                null,
                null,
                "contact@bank.test",
                null,
                null,
                null,
                "COMPANY"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/bank-contacts")
                    .header("Authorization", principal.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private UUID createBankRequest(
      TestPrincipal principal, UUID caseId, UUID bankId, UUID bankContactId) throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateBankRequestApiRequest(bankId, bankContactId));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/bank-requests")
                    .header("Authorization", principal.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private UUID createOffer(TestPrincipal principal, UUID bankRequestId, String amount)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateBankOfferApiRequest(
                new BigDecimal(amount),
                new BigDecimal("3.2"),
                300,
                new BigDecimal("900.00"),
                Map.of()));
    String response =
        mockMvc
            .perform(
                post("/api/v1/bank-requests/" + bankRequestId + "/offers")
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
  void bankRequestCreateWithContactSnapshotThenListAndGet() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br1");
    UUID bankId = createBank(superadmin, "BR1");
    UUID companyId = companyRepository.insert("Co BR1", "Co BR1", "TC-BR1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-br1");
    UUID contactId = createBankContact(manager, bankId, "Original Name");
    UUID caseId = createCase(manager);

    UUID bankRequestId = createBankRequest(manager, caseId, bankId, contactId);

    mockMvc
        .perform(
            get("/api/v1/bank-requests/" + bankRequestId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.contactSnapshot.name").value("Original Name"));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/bank-requests")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    Activity activity =
        activityRepository.findAllByCaseId(caseId).stream()
            .filter(a -> "bank.request.created".equals(a.activityType()))
            .findFirst()
            .orElseThrow();
    assertThat(activity.caseId()).isEqualTo(caseId);
  }

  @Test
  void contactSnapshotRemainsImmutableAfterContactIsLaterUpdated() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br2");
    UUID bankId = createBank(superadmin, "BR2");
    UUID companyId = companyRepository.insert("Co BR2", "Co BR2", "TC-BR2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-br2");
    UUID contactId = createBankContact(manager, bankId, "Before Update");
    UUID caseId = createCase(manager);

    UUID bankRequestId = createBankRequest(manager, caseId, bankId, contactId);

    String updateBody =
        objectMapper.writeValueAsString(
            new UpdateBankContactApiRequest(
                "After Update",
                "Manager",
                null,
                null,
                "contact@bank.test",
                null,
                null,
                null,
                "COMPANY",
                true));
    mockMvc
        .perform(
            patch("/api/v1/bank-contacts/" + contactId)
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/bank-requests/" + bankRequestId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.contactSnapshot.name").value("Before Update"));
  }

  @Test
  void bankResponseRegistrationWritesActivityAndOfferCreationDoesNot() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br3");
    UUID bankId = createBank(superadmin, "BR3");
    UUID companyId = companyRepository.insert("Co BR3", "Co BR3", "TC-BR3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-br3");
    UUID caseId = createCase(manager);
    UUID bankRequestId = createBankRequest(manager, caseId, bankId, null);

    String responseBody =
        objectMapper.writeValueAsString(
            new CreateBankResponseApiRequest(
                "Bank asked for more documentation", Map.of("k", "v")));
    mockMvc
        .perform(
            post("/api/v1/bank-requests/" + bankRequestId + "/responses")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(responseBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECEIVED"))
        .andExpect(jsonPath("$.summary").value("Bank asked for more documentation"));

    long responseActivities =
        activityRepository.findAllByCaseId(caseId).stream()
            .filter(a -> "bank.response.received".equals(a.activityType()))
            .count();
    assertThat(responseActivities).isEqualTo(1);

    createOffer(manager, bankRequestId, "150000");

    long bankRelatedActivities =
        activityRepository.findAllByCaseId(caseId).stream()
            .filter(a -> a.activityType().startsWith("bank."))
            .count();
    assertThat(bankRelatedActivities)
        .isEqualTo(2); // bank.request.created + bank.response.received only
  }

  @Test
  void offerListAndGetWorkForAssignedBroker() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br4");
    UUID bankId = createBank(superadmin, "BR4");
    UUID companyId = companyRepository.insert("Co BR4", "Co BR4", "TC-BR4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-br4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-br4");
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);
    UUID bankRequestId = createBankRequest(manager, caseId, bankId, null);
    UUID offerId = createOffer(manager, bankRequestId, "200000");

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/offers").header("Authorization", broker.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));

    mockMvc
        .perform(get("/api/v1/bank-offers/" + offerId).header("Authorization", broker.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECEIVED"))
        .andExpect(jsonPath("$.amount").value(200000));
  }

  @Test
  void brokerWithoutCaseAssignmentCannotCreateBankRequest() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br5");
    UUID bankId = createBank(superadmin, "BR5");
    UUID companyId = companyRepository.insert("Co BR5", "Co BR5", "TC-BR5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-br5");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-br5");
    UUID caseId = createCase(manager);

    String body = objectMapper.writeValueAsString(new CreateBankRequestApiRequest(bankId, null));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/bank-requests")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void bankRequestFromAnotherTenantIsNotFound() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br6");
    UUID bankId = createBank(superadmin, "BR6");
    UUID companyA = companyRepository.insert("Co BR6A", "Co BR6A", "TC-BR6A");
    UUID companyB = companyRepository.insert("Co BR6B", "Co BR6B", "TC-BR6B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-br6a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-br6b");
    UUID caseBId = createCase(managerB);
    UUID bankRequestId = createBankRequest(managerB, caseBId, bankId, null);

    mockMvc
        .perform(
            get("/api/v1/bank-requests/" + bankRequestId)
                .header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void bankOfferFromAnotherTenantIsNotFoundAcrossBothHops() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br7");
    UUID bankId = createBank(superadmin, "BR7");
    UUID companyA = companyRepository.insert("Co BR7A", "Co BR7A", "TC-BR7A");
    UUID companyB = companyRepository.insert("Co BR7B", "Co BR7B", "TC-BR7B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-br7a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-br7b");
    UUID caseBId = createCase(managerB);
    UUID bankRequestId = createBankRequest(managerB, caseBId, bankId, null);
    UUID offerId = createOffer(managerB, bankRequestId, "90000");

    mockMvc
        .perform(get("/api/v1/bank-offers/" + offerId).header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void superadminWithoutSupportSessionCannotAccessBankRequests() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br8");
    UUID companyId = companyRepository.insert("Co BR8", "Co BR8", "TC-BR8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-br8");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/bank-requests")
                .header("Authorization", superadmin.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void clientCannotAccessBankRequests() throws Exception {
    UUID companyId = companyRepository.insert("Co BR9", "Co BR9", "TC-BR9");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-br9");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-br9");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/bank-requests")
                .header("Authorization", client.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void selectingSecondOfferUpdatesSameFinalFinancingRowAndLeavesOtherOfferUntouched()
      throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-br10");
    UUID bankId = createBank(superadmin, "BR10");
    UUID companyId = companyRepository.insert("Co BR10", "Co BR10", "TC-BR10");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-br10");
    UUID caseId = createCase(manager);
    UUID bankRequestId = createBankRequest(manager, caseId, bankId, null);
    UUID offerAId = createOffer(manager, bankRequestId, "100000");
    UUID offerBId = createOffer(manager, bankRequestId, "110000");

    String firstSelectResponse =
        mockMvc
            .perform(
                post("/api/v1/bank-offers/" + offerAId + "/select")
                    .header("Authorization", manager.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bankOfferId").value(offerAId.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID finalFinancingId =
        UUID.fromString(objectMapper.readTree(firstSelectResponse).get("id").asText());

    mockMvc
        .perform(
            post("/api/v1/bank-offers/" + offerBId + "/select")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(finalFinancingId.toString()))
        .andExpect(jsonPath("$.bankOfferId").value(offerBId.toString()));

    Optional<FinalFinancing> stored = finalFinancingRepository.findByCaseId(caseId);
    assertThat(stored).isPresent();
    assertThat(stored.get().id()).isEqualTo(finalFinancingId);
    assertThat(stored.get().bankOfferId()).isEqualTo(offerBId);

    mockMvc
        .perform(get("/api/v1/bank-offers/" + offerAId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RECEIVED"));
  }
}
