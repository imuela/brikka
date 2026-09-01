package com.brika.platform.scoring;

import com.brika.platform.document.CaseChecklist;
import com.brika.platform.document.CaseChecklistService;
import com.brika.platform.financialanalysis.FinancialAnalysisResult;
import com.brika.platform.financialanalysis.FinancialAnalysisResultRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * BRIKKA V2 I2. Builds the case's qualitative RAG indicator from signals the platform already
 * produces — it does NOT recompute anything and adds no business variable:
 *
 * <ul>
 *   <li><b>scoring</b>: category of the most recent {@code scoring_results} row of the case (the
 *       existing ScoringEngine output, seeded ruleset {@code default-operation-v1}). Its
 *       GREEN/AMBER/RED categories map 1:1; any other category name contributes no signal.
 *   <li><b>viability</b>: worst {@code viabilityCategory} among the latest per-client {@code
 *       case_financial_analysis_results} (FAVORABLE→GREEN, REVISAR→AMBER, NO_VIABLE→RED).
 *   <li><b>documentation</b>: the I1 {@link CaseChecklistService} completeness of mandatory docs.
 * </ul>
 *
 * <p>Every query is filtered by {@code companyId} so a case from another tenant can never yield its
 * indicator (the controller already resolves the tenant from the authenticated identity). The
 * combined level is the worst of the axes that could be evaluated; if none could, the whole
 * indicator is {@code NOT_EVALUATED}. Deterministic: the same stored data always yields the same
 * indicator.
 *
 * <p>Lives in the {@code scoring} package and only reads from {@code financialanalysis} / {@code
 * document} — neither of those imports {@code scoring}, so there is no package cycle.
 */
@Service
public class CaseRagService {

  private final ScoringResultRepository scoringResultRepository;
  private final FinancialAnalysisResultRepository financialAnalysisResultRepository;
  private final CaseChecklistService caseChecklistService;

  public CaseRagService(
      ScoringResultRepository scoringResultRepository,
      FinancialAnalysisResultRepository financialAnalysisResultRepository,
      CaseChecklistService caseChecklistService) {
    this.scoringResultRepository = scoringResultRepository;
    this.financialAnalysisResultRepository = financialAnalysisResultRepository;
    this.caseChecklistService = caseChecklistService;
  }

  public CaseRagIndicator evaluate(
      UUID companyId, UUID caseId, String operationType, List<UUID> holderClientIds) {
    List<RagAxis> axes =
        List.of(
            scoringAxis(companyId, caseId),
            viabilityAxis(companyId, caseId),
            documentationAxis(caseId, operationType, holderClientIds));
    return new CaseRagIndicator(combine(axes), axes);
  }

  /** Worst of the axes that were actually evaluated; NOT_EVALUATED if none were. */
  private RagLevel combine(List<RagAxis> axes) {
    RagLevel worst = RagLevel.NOT_EVALUATED;
    boolean anyEvaluated = false;
    for (RagAxis axis : axes) {
      if (axis.level() == RagLevel.NOT_EVALUATED) {
        continue;
      }
      anyEvaluated = true;
      if (axis.level().severity() > worst.severity()) {
        worst = axis.level();
      }
    }
    return anyEvaluated ? worst : RagLevel.NOT_EVALUATED;
  }

  private RagAxis scoringAxis(UUID companyId, UUID caseId) {
    return scoringResultRepository.findAllByCaseId(caseId).stream()
        .filter(result -> result.companyId().equals(companyId))
        .findFirst() // repository orders by calculated_at DESC → first row is the most recent run
        .map(
            result ->
                new RagAxis(
                    "scoring",
                    ragFromScoringCategory(result.category()),
                    "Categoría "
                        + result.category()
                        + " (puntuación "
                        + result.totalScore().toPlainString()
                        + ")"))
        .orElse(
            new RagAxis("scoring", RagLevel.NOT_EVALUATED, "Scoring de la operación no calculado"));
  }

  private RagLevel ragFromScoringCategory(String category) {
    try {
      return RagLevel.valueOf(category);
    } catch (IllegalArgumentException e) {
      // A custom admin-authored ruleset may use non-RAG category names; it then contributes no
      // signal to the indicator rather than being force-fitted into a colour.
      return RagLevel.NOT_EVALUATED;
    }
  }

  private RagAxis viabilityAxis(UUID companyId, UUID caseId) {
    List<FinancialAnalysisResult> tenantResults =
        financialAnalysisResultRepository.findAllByCaseId(caseId).stream()
            .filter(result -> result.companyId().equals(companyId))
            .toList();
    if (tenantResults.isEmpty()) {
      return new RagAxis(
          "viability", RagLevel.NOT_EVALUATED, "Análisis de viabilidad no ejecutado");
    }

    // findAllByCaseId is ordered calculated_at DESC → the first row seen per client is its latest.
    Map<UUID, FinancialAnalysisResult> latestByClient = new LinkedHashMap<>();
    for (FinancialAnalysisResult result : tenantResults) {
      latestByClient.putIfAbsent(result.clientId(), result);
    }

    RagLevel worst = RagLevel.NOT_EVALUATED;
    String worstCategory = null;
    for (FinancialAnalysisResult result : latestByClient.values()) {
      RagLevel level = ragFromViabilityCategory(result.viabilityCategory());
      if (level.severity() > worst.severity()) {
        worst = level;
        worstCategory = result.viabilityCategory();
      }
    }
    if (worst == RagLevel.NOT_EVALUATED) {
      return new RagAxis(
          "viability", RagLevel.NOT_EVALUATED, "Análisis de viabilidad sin categoría reconocida");
    }
    String detail =
        latestByClient.size() == 1
            ? "Viabilidad " + worstCategory
            : "Peor viabilidad entre " + latestByClient.size() + " titulares: " + worstCategory;
    return new RagAxis("viability", worst, detail);
  }

  private RagLevel ragFromViabilityCategory(String category) {
    return switch (category) {
      case "FAVORABLE" -> RagLevel.GREEN;
      case "REVISAR" -> RagLevel.AMBER;
      case "NO_VIABLE" -> RagLevel.RED;
      default -> RagLevel.NOT_EVALUATED;
    };
  }

  private RagAxis documentationAxis(UUID caseId, String operationType, List<UUID> holderClientIds) {
    CaseChecklist checklist =
        caseChecklistService.checklist(caseId, operationType, holderClientIds);
    if (checklist.mandatoryTotal() == 0) {
      return new RagAxis(
          "documentation",
          RagLevel.NOT_EVALUATED,
          "Sin requisitos documentales para este tipo de operación");
    }
    if (checklist.mandatoryMissing() == 0) {
      return new RagAxis("documentation", RagLevel.GREEN, "Documentación obligatoria completa");
    }
    int approved = checklist.mandatoryTotal() - checklist.mandatoryMissing();
    RagLevel level = approved == 0 ? RagLevel.RED : RagLevel.AMBER;
    return new RagAxis(
        "documentation",
        level,
        "Documentación obligatoria: " + approved + "/" + checklist.mandatoryTotal() + " aprobados");
  }
}
