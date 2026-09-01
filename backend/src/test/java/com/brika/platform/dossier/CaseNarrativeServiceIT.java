package com.brika.platform.dossier;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.crm.ClientFinancialProfileRepository;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.financialanalysis.FinancialAnalysisResultRepository;
import com.brika.platform.financing.FinancingRequestRepository;
import com.brika.platform.financing.SimulationRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.property.PropertyRepository;
import com.brika.platform.scoring.ScoringResultRepository;
import com.brika.platform.scoring.ScoringRulesetRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BRIKKA V2 I5. The dossier narrative is deterministic (same stored data → same text), always
 * covers every section (with an explicit "no disponible" sentence when data is missing), and never
 * surfaces another tenant's scoring / viability rows.
 */
@Testcontainers
@SpringBootTest
class CaseNarrativeServiceIT {

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

  @Autowired private CaseNarrativeService narrativeService;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private CaseService caseService;
  @Autowired private CaseClientRepository caseClientRepository;
  @Autowired private ClientRepository clientRepository;
  @Autowired private ClientFinancialProfileRepository financialProfileRepository;
  @Autowired private PropertyRepository propertyRepository;
  @Autowired private FinancingRequestRepository financingRequestRepository;
  @Autowired private SimulationRepository simulationRepository;
  @Autowired private ScoringResultRepository scoringResultRepository;
  @Autowired private ScoringRulesetRepository scoringRulesetRepository;
  @Autowired private FinancialAnalysisResultRepository financialAnalysisResultRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

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

  private UUID addHolder(UUID companyId, Case theCase, String first, String last) {
    UUID clientId =
        clientRepository.insert(
            companyId, first, last, "cli-" + UUID.randomUUID() + "@brika.test", "600");
    caseClientRepository.insert(theCase.id(), clientId, ParticipationType.HOLDER, true);
    return clientId;
  }

  private void addProfile(UUID companyId, UUID clientId, User actor, String income) {
    financialProfileRepository.insert(
        companyId,
        clientId,
        "SINGLE",
        0,
        "asalariado",
        "indefinido",
        "ACME",
        5,
        new BigDecimal(income),
        new BigDecimal("20000"),
        new BigDecimal("100"),
        BigDecimal.ZERO,
        "BROKER",
        "CONFIRMED",
        null,
        actor.id());
  }

  private void addScoringResult(UUID companyId, UUID caseId, String category, String total) {
    UUID rulesetId = scoringRulesetRepository.findAllActive().get(0).id();
    scoringResultRepository.insert(
        companyId, caseId, rulesetId, new BigDecimal(total), category, "{}");
  }

  private void addViability(UUID companyId, UUID caseId, UUID clientId, User actor, String cat) {
    financialAnalysisResultRepository.insert(
        companyId,
        caseId,
        clientId,
        new BigDecimal("180000"),
        new BigDecimal("0.03"),
        360,
        new BigDecimal("760"),
        new BigDecimal("3000"),
        BigDecimal.ZERO,
        new BigDecimal("25.30"),
        cat,
        "SIMULATION",
        UUID.randomUUID(),
        "v1",
        "{}",
        actor.id());
  }

  private String textOf(CaseNarrative narrative, String key) {
    return narrative.sections().stream()
        .filter(s -> s.key().equals(key))
        .findFirst()
        .orElseThrow()
        .paragraphs()
        .stream()
        .reduce("", (a, b) -> a + " " + b);
  }

  @Test
  void fullyInformedCaseHasEverySectionPopulated() {
    UUID companyId = newCompany("TC-NAR1");
    User manager = newManager(companyId, "m-nar1");
    Case theCase = caseService.createCase(companyId, manager.id(), "PURCHASE");
    UUID holder1 = addHolder(companyId, theCase, "Ada", "Lovelace");
    UUID holder2 = addHolder(companyId, theCase, "Alan", "Turing");
    addProfile(companyId, holder1, manager, "3000");
    addProfile(companyId, holder2, manager, "2500");
    propertyRepository.upsert(
        companyId,
        theCase.id(),
        "{\"city\":\"Madrid\"}",
        "FLAT",
        new BigDecimal("250000"),
        new BigDecimal("240000"));
    financingRequestRepository.insert(companyId, theCase.id(), new BigDecimal("180000"), 360);
    simulationRepository.insert(
        companyId,
        theCase.id(),
        new BigDecimal("180000"),
        new BigDecimal("3.0000"),
        360,
        new BigDecimal("759.00"),
        "{}",
        manager.id());
    addScoringResult(companyId, theCase.id(), "GREEN", "100.00");
    addViability(companyId, theCase.id(), holder1, manager, "FAVORABLE");
    addViability(companyId, theCase.id(), holder2, manager, "REVISAR");

    CaseNarrative narrative = narrativeService.narrate(theCase);

    assertThat(narrative.sections())
        .extracting(NarrativeSection::key)
        .containsExactly(
            "situation",
            "holders",
            "property",
            "financing",
            "scoring",
            "viability",
            "documentation",
            "fees");
    assertThat(textOf(narrative, "situation")).contains(theCase.reference()).contains("Preestudio");
    assertThat(textOf(narrative, "holders"))
        .contains("Ada Lovelace")
        .contains("Alan Turing")
        .contains("ingresos mensuales");
    assertThat(textOf(narrative, "property")).contains("LTV aproximado");
    assertThat(textOf(narrative, "financing"))
        .contains("Simulación más reciente")
        .contains("tipo de interés fijo");
    assertThat(textOf(narrative, "scoring"))
        .contains("categoría GREEN")
        .contains("Indicador RAG del expediente");
    assertThat(textOf(narrative, "viability")).contains("FAVORABLE").contains("REVISAR");
    assertThat(textOf(narrative, "documentation")).contains("Documentación obligatoria");
    assertThat(textOf(narrative, "fees")).contains("Sin honorarios configurados");
  }

  @Test
  void sparseCaseSaysDataIsNotAvailable() {
    UUID companyId = newCompany("TC-NAR2");
    User manager = newManager(companyId, "m-nar2");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    addHolder(companyId, theCase, "Grace", "Hopper");

    CaseNarrative narrative = narrativeService.narrate(theCase);

    assertThat(textOf(narrative, "holders")).contains("sin perfil financiero registrado");
    assertThat(textOf(narrative, "property")).contains("Sin inmueble registrado");
    assertThat(textOf(narrative, "financing"))
        .contains("Sin simulaciones ni solicitudes de financiación");
    assertThat(textOf(narrative, "scoring"))
        .contains("Scoring de la operación no calculado")
        .contains("sin evaluar");
    assertThat(textOf(narrative, "viability")).contains("Sin análisis de viabilidad ejecutado");
    assertThat(textOf(narrative, "documentation"))
        .contains("Sin requisitos documentales para este tipo de operación");
    assertThat(textOf(narrative, "fees")).contains("Sin honorarios configurados");
  }

  @Test
  void narrativeIsDeterministic() {
    UUID companyId = newCompany("TC-NAR3");
    User manager = newManager(companyId, "m-nar3");
    Case theCase = caseService.createCase(companyId, manager.id(), "PURCHASE");
    UUID holder = addHolder(companyId, theCase, "Edsger", "Dijkstra");
    addProfile(companyId, holder, manager, "4000");
    addScoringResult(companyId, theCase.id(), "AMBER", "55.00");

    CaseNarrative first = narrativeService.narrate(theCase);
    CaseNarrative second = narrativeService.narrate(theCase);

    assertThat(first).isEqualTo(second);
  }

  @Test
  void anotherTenantScoringAndViabilityNeverAppear() {
    UUID companyId = newCompany("TC-NAR4");
    UUID otherCompanyId = newCompany("TC-NAR4-OTHER");
    User manager = newManager(companyId, "m-nar4");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    UUID holder = addHolder(companyId, theCase, "Barbara", "Liskov");

    addScoringResult(otherCompanyId, theCase.id(), "RED", "10.00");
    addViability(otherCompanyId, theCase.id(), holder, manager, "NO_VIABLE");

    CaseNarrative narrative = narrativeService.narrate(theCase);

    assertThat(textOf(narrative, "scoring")).contains("Scoring de la operación no calculado");
    assertThat(textOf(narrative, "scoring")).doesNotContain("RED");
    assertThat(textOf(narrative, "viability")).contains("Sin análisis de viabilidad ejecutado");
    assertThat(textOf(narrative, "viability")).doesNotContain("NO_VIABLE");
  }

  @Test
  void multipleHoldersAreAllNamed() {
    UUID companyId = newCompany("TC-NAR5");
    User manager = newManager(companyId, "m-nar5");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    addHolder(companyId, theCase, "Katherine", "Johnson");
    addHolder(companyId, theCase, "Dorothy", "Vaughan");
    addHolder(companyId, theCase, "Mary", "Jackson");

    String holders = textOf(narrativeService.narrate(theCase), "holders");

    assertThat(holders).contains("3 titulares");
    assertThat(holders)
        .contains("Katherine Johnson")
        .contains("Dorothy Vaughan")
        .contains("Mary Jackson");
  }

  @Test
  void theMostRecentSimulationIsTheOneDescribed() {
    UUID companyId = newCompany("TC-NAR6");
    User manager = newManager(companyId, "m-nar6");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    addHolder(companyId, theCase, "Ada", "Byron");

    simulationRepository.insert(
        companyId,
        theCase.id(),
        new BigDecimal("100000"),
        new BigDecimal("5.5000"),
        240,
        new BigDecimal("688.00"),
        "{}",
        manager.id());
    // Nudge created_at so ordering is unambiguous, then insert the newer one.
    jdbcTemplate.update("UPDATE simulations SET created_at = now() - interval '1 hour'");
    simulationRepository.insert(
        companyId,
        theCase.id(),
        new BigDecimal("200000"),
        new BigDecimal("2.1000"),
        360,
        new BigDecimal("753.00"),
        "{}",
        manager.id());

    String financing = textOf(narrativeService.narrate(theCase), "financing");

    assertThat(financing).contains("Se han registrado 2 simulaciones");
    assertThat(financing).contains("tipo final 2.1");
    assertThat(financing).doesNotContain("5.5");
  }

  @Test
  void aCaseWithoutHoldersDoesNotBreakTheNarrative() {
    UUID companyId = newCompany("TC-NAR7");
    User manager = newManager(companyId, "m-nar7");
    Case theCase = caseService.createCase(companyId, manager.id(), "MORTGAGE");

    CaseNarrative narrative = narrativeService.narrate(theCase);

    assertThat(narrative.sections()).hasSize(8);
    assertThat(textOf(narrative, "holders")).contains("no tiene titulares asociados");
    assertThat(narrative).isEqualTo(narrativeService.narrate(theCase));
  }
}
