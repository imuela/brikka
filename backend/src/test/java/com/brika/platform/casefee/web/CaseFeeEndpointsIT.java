package com.brika.platform.casefee.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.CaseService;
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
 * Sprint 32. End-to-end tests for case fees, mirroring FinancialAnalysisEndpointsIT's structure
 * (real HTTP through the actual SecurityFilterChain/controllers).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class CaseFeeEndpointsIT {

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
  @Autowired private CaseService caseService;

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

  private UUID createCase(UUID companyId, TestPrincipal actor) {
    return caseService.createCase(companyId, actor.user().id(), "PURCHASE").id();
  }

  private String fixedFeeJson(String amount, String status) throws Exception {
    return objectMapper.writeValueAsString(
        new UpsertCaseFeeApiRequest(
            "FIXED", new java.math.BigDecimal(amount), null, null, status, null));
  }

  private String percentageFeeJson(String percentage, String base, String status) throws Exception {
    return objectMapper.writeValueAsString(
        new UpsertCaseFeeApiRequest(
            "PERCENTAGE",
            null,
            new java.math.BigDecimal(percentage),
            new java.math.BigDecimal(base),
            status,
            null));
  }

  @Test
  void managerConfiguresAFixedFeeAndReadsItBack() throws Exception {
    UUID companyId = companyRepository.insert("Co CF1", "Co CF1", "TC-CF1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cf1");
    UUID caseId = createCase(companyId, manager);

    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/fee")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixedFeeJson("1500.00", "PROPOSED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.feeType").value("FIXED"))
        .andExpect(jsonPath("$.calculatedAmount").value(1500.00))
        .andExpect(jsonPath("$.status").value("PROPOSED"));

    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/fee").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calculatedAmount").value(1500.00));
  }

  @Test
  void percentageFeeIsComputedFromTheExplicitCalculationBase() throws Exception {
    UUID companyId = companyRepository.insert("Co CF2", "Co CF2", "TC-CF2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cf2");
    UUID caseId = createCase(companyId, manager);

    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/fee")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(percentageFeeJson("2.5", "200000.00", "PROPOSED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calculatedAmount").value(5000.00)); // 200000 * 2.5 / 100
  }

  @Test
  void upsertOverwritesTheCurrentFeeAndWritesHistory() throws Exception {
    UUID companyId = companyRepository.insert("Co CF3", "Co CF3", "TC-CF3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cf3");
    UUID caseId = createCase(companyId, manager);

    mockMvc.perform(
        put("/api/v1/cases/" + caseId + "/fee")
            .header("Authorization", manager.bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(fixedFeeJson("1000.00", "PROPOSED")));
    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/fee")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixedFeeJson("1200.00", "AGREED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.calculatedAmount").value(1200.00))
        .andExpect(jsonPath("$.status").value("AGREED"));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/fee/history")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void negativeFixedAmountIsRejectedWithA400() throws Exception {
    UUID companyId = companyRepository.insert("Co CF4", "Co CF4", "TC-CF4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cf4");
    UUID caseId = createCase(companyId, manager);

    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/fee")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixedFeeJson("-100.00", "PROPOSED")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("NEGATIVE_FEE_VALUE"));
  }

  @Test
  void percentageOver100IsRejectedWithA400() throws Exception {
    UUID companyId = companyRepository.insert("Co CF5", "Co CF5", "TC-CF5");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cf5");
    UUID caseId = createCase(companyId, manager);

    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/fee")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(percentageFeeJson("150", "100000", "PROPOSED")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PERCENTAGE"));
  }

  @Test
  void brokerNotAssignedToTheCaseIsForbidden() throws Exception {
    UUID companyId = companyRepository.insert("Co CF6", "Co CF6", "TC-CF6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cf6");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-cf6");
    UUID caseId = createCase(companyId, manager);

    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/fee")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixedFeeJson("500", "PROPOSED")))
        .andExpect(status().isForbidden());
  }

  @Test
  void superadminIsGlobalAcrossTenants() throws Exception {
    UUID companyId = companyRepository.insert("Co CF7", "Co CF7", "TC-CF7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cf7");
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-cf7");
    UUID caseId = createCase(companyId, manager);

    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/fee")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixedFeeJson("750", "PROPOSED")))
        .andExpect(status().isOk());
  }

  @Test
  void managerFromAnotherCompanyGetsAMaskedNotFoundNotForbidden() throws Exception {
    UUID companyA = companyRepository.insert("Co CF8A", "Co CF8A", "TC-CF8A");
    UUID companyB = companyRepository.insert("Co CF8B", "Co CF8B", "TC-CF8B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-cf8a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-cf8b");
    UUID caseId = createCase(companyA, managerA);

    mockMvc
        .perform(get("/api/v1/cases/" + caseId + "/fee").header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            put("/api/v1/cases/" + caseId + "/fee")
                .header("Authorization", managerB.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixedFeeJson("100", "PROPOSED")))
        .andExpect(status().isNotFound());
  }

  @Test
  void unauthenticatedRequestIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co CF9", "Co CF9", "TC-CF9");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cf9");
    UUID caseId = createCase(companyId, manager);

    mockMvc.perform(get("/api/v1/cases/" + caseId + "/fee")).andExpect(status().isUnauthorized());
  }
}
