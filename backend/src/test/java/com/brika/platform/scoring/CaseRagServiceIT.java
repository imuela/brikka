package com.brika.platform.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.financialanalysis.FinancialAnalysisResultRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BRIKKA V2 I2. Deterministic qualitative RAG indicator of a case ({@link CaseRagService}). The
 * three axes (scoring / viability / documentation) are combined as "worst of the axes that could be
 * evaluated"; {@code NOT_EVALUATED} never worsens the result and a fully-unevaluated case is itself
 * {@code NOT_EVALUATED}. Every axis query is tenant-scoped: data belonging to another company can
 * never leak into a case's indicator.
 */
@Testcontainers
@SpringBootTest
class CaseRagServiceIT {

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

  @Autowired private CaseRagService caseRagService;
  @Autowired private CaseService caseService;
  @Autowired private CaseClientRepository caseClientRepository;
  @Autowired private ClientRepository clientRepository;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private ScoringResultRepository scoringResultRepository;
  @Autowired private ScoringRulesetRepository scoringRulesetRepository;
  @Autowired private FinancialAnalysisResultRepository financialAnalysisResultRepository;

  private UUID factoryRulesetId() {
    return scoringRulesetRepository.findAllActive().stream()
        .filter(rs -> "default-operation-v1".equals(rs.code()))
        .findFirst()
        .orElseThrow()
        .id();
  }

  private UUID newCompany(String taxId) {
    return companyRepository.insert("Co " + taxId, "Co " + taxId, taxId);
  }

  private User newManager(UUID companyId, String prefix) {
    return userProvisioningService.createUser(
        new CreateUserCommand(
            UserRole.MANAGER,
            companyId,
            "ext-" + UUID.randomUUID(),
            prefix + "-" + UUID.randomUUID() + "@brika.test",
            "M",
            "Gr"));
  }

  private Case newCase(UUID companyId, User manager, String operationType) {
    Case theCase = caseService.createCase(companyId, manager.id(), operationType);
    UUID clientId =
        clientRepository.insert(
            companyId, "Cli", "Ent", "cli-" + UUID.randomUUID() + "@brika.test", "600");
    caseClientRepository.insert(theCase.id(), clientId, ParticipationType.HOLDER, true);
    return theCase;
  }

  private void insertScoringResult(UUID companyId, UUID caseId, String category, String total) {
    scoringResultRepository.insert(
        companyId, caseId, factoryRulesetId(), new BigDecimal(total), category, "{}");
  }

  private void insertViability(
      UUID companyId, UUID caseId, UUID clientId, UUID calculatedBy, String category) {
    financialAnalysisResultRepository.insert(
        companyId,
        caseId,
        clientId,
        new BigDecimal("180000"),
        new BigDecimal("0.0300"),
        360,
        new BigDecimal("760.00"),
        new BigDecimal("3000.00"),
        new BigDecimal("0.00"),
        new BigDecimal("25.33"),
        category,
        "SIMULATION",
        UUID.randomUUID(),
        "v1",
        "{}",
        calculatedBy);
  }

  private RagAxis axis(CaseRagIndicator indicator, String name) {
    return indicator.axes().stream().filter(a -> a.axis().equals(name)).findFirst().orElseThrow();
  }

  private CaseRagIndicator evaluate(UUID companyId, Case theCase) {
    List<UUID> holders =
        caseClientRepository.findAllByCaseId(theCase.id()).stream()
            .map(cc -> cc.clientId())
            .toList();
    return caseRagService.evaluate(companyId, theCase.id(), theCase.operationType(), holders);
  }

  @Test
  void aCaseWithNoSignalsAtAllIsNotEvaluated() {
    UUID companyId = newCompany("TC-RAG1");
    User manager = newManager(companyId, "m-rag1");
    Case theCase = newCase(companyId, manager, "REFINANCE"); // no document_requirements catalog

    CaseRagIndicator indicator = evaluate(companyId, theCase);

    assertThat(indicator.level()).isEqualTo(RagLevel.NOT_EVALUATED);
    assertThat(indicator.axes()).extracting(RagAxis::level).containsOnly(RagLevel.NOT_EVALUATED);
  }

  @Test
  void allFavourableSignalsYieldGreen() {
    UUID companyId = newCompany("TC-RAG2");
    User manager = newManager(companyId, "m-rag2");
    Case theCase = newCase(companyId, manager, "REFINANCE");
    UUID clientId = caseClientRepository.findAllByCaseId(theCase.id()).get(0).clientId();

    insertScoringResult(companyId, theCase.id(), "GREEN", "100.00");
    insertViability(companyId, theCase.id(), clientId, manager.id(), "FAVORABLE");

    CaseRagIndicator indicator = evaluate(companyId, theCase);

    assertThat(indicator.level()).isEqualTo(RagLevel.GREEN);
    assertThat(axis(indicator, "scoring").level()).isEqualTo(RagLevel.GREEN);
    assertThat(axis(indicator, "viability").level()).isEqualTo(RagLevel.GREEN);
    assertThat(axis(indicator, "documentation").level()).isEqualTo(RagLevel.NOT_EVALUATED);
  }

  @Test
  void theWorstEvaluatedAxisDrivesTheCombinedLevel() {
    UUID companyId = newCompany("TC-RAG3");
    User manager = newManager(companyId, "m-rag3");
    Case theCase = newCase(companyId, manager, "REFINANCE");
    UUID clientId = caseClientRepository.findAllByCaseId(theCase.id()).get(0).clientId();

    insertScoringResult(companyId, theCase.id(), "GREEN", "90.00");
    insertViability(companyId, theCase.id(), clientId, manager.id(), "REVISAR"); // AMBER

    CaseRagIndicator indicator = evaluate(companyId, theCase);

    assertThat(indicator.level()).isEqualTo(RagLevel.AMBER);
  }

  @Test
  void aNoViableClientForcesRedEvenWithAGreenScore() {
    UUID companyId = newCompany("TC-RAG4");
    User manager = newManager(companyId, "m-rag4");
    Case theCase = newCase(companyId, manager, "REFINANCE");
    UUID clientId = caseClientRepository.findAllByCaseId(theCase.id()).get(0).clientId();

    insertScoringResult(companyId, theCase.id(), "GREEN", "80.00");
    insertViability(companyId, theCase.id(), clientId, manager.id(), "NO_VIABLE");

    CaseRagIndicator indicator = evaluate(companyId, theCase);

    assertThat(indicator.level()).isEqualTo(RagLevel.RED);
    assertThat(axis(indicator, "viability").level()).isEqualTo(RagLevel.RED);
  }

  @Test
  void missingMandatoryDocumentationIsRedOnItsOwnAxis() {
    UUID companyId = newCompany("TC-RAG5");
    User manager = newManager(companyId, "m-rag5");
    // PURCHASE has mandatory PER_CASE requirements (V27) and nothing is approved on a fresh case.
    Case theCase = newCase(companyId, manager, "PURCHASE");

    CaseRagIndicator indicator = evaluate(companyId, theCase);

    assertThat(axis(indicator, "documentation").level()).isEqualTo(RagLevel.RED);
    assertThat(indicator.level()).isEqualTo(RagLevel.RED);
  }

  @Test
  void scoringResultOfAnotherTenantNeverLeaksIntoTheIndicator() {
    UUID companyId = newCompany("TC-RAG6");
    User manager = newManager(companyId, "m-rag6");
    Case theCase = newCase(companyId, manager, "REFINANCE");
    UUID clientId = caseClientRepository.findAllByCaseId(theCase.id()).get(0).clientId();
    UUID otherCompanyId = newCompany("TC-RAG6-OTHER");

    // Rows physically on this case_id but stamped with another company_id.
    insertScoringResult(otherCompanyId, theCase.id(), "RED", "10.00");
    insertViability(otherCompanyId, theCase.id(), clientId, manager.id(), "NO_VIABLE");

    CaseRagIndicator indicator = evaluate(companyId, theCase);

    assertThat(axis(indicator, "scoring").level()).isEqualTo(RagLevel.NOT_EVALUATED);
    assertThat(axis(indicator, "viability").level()).isEqualTo(RagLevel.NOT_EVALUATED);
    assertThat(indicator.level()).isEqualTo(RagLevel.NOT_EVALUATED);
  }

  @Test
  void theIndicatorIsDeterministicForTheSameStoredData() {
    UUID companyId = newCompany("TC-RAG7");
    User manager = newManager(companyId, "m-rag7");
    Case theCase = newCase(companyId, manager, "REFINANCE");
    UUID clientId = caseClientRepository.findAllByCaseId(theCase.id()).get(0).clientId();

    insertScoringResult(companyId, theCase.id(), "AMBER", "55.00");
    insertViability(companyId, theCase.id(), clientId, manager.id(), "FAVORABLE");

    CaseRagIndicator first = evaluate(companyId, theCase);
    CaseRagIndicator second = evaluate(companyId, theCase);

    assertThat(first.level()).isEqualTo(RagLevel.AMBER);
    assertThat(first).isEqualTo(second);
  }
}
