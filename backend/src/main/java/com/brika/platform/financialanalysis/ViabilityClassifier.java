package com.brika.platform.financialanalysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sprint 31. Computes DTI and classifies it into a deterministic, explicit 3-band viability
 * indicator. No documented, approved DTI/viability thresholds exist anywhere in this project
 * (searched 03_DOMAIN_SPECIFICATION.md, 07_DATA_GOVERNANCE_SPECIFICATION.md, 12_DECISION_LOG.md,
 * 15_DEFINITIVE_ERD.md, Brikka Legacy — the only DTI mention in the whole documentation set is
 * 07_DATA_GOVERNANCE_SPECIFICATION.md §5 listing "DTI calculado" as an example of a derived value,
 * with no formula or threshold attached). Per this sprint's explicit instruction, the thresholds
 * below are therefore Brikka's own internal, orientative defaults — not a banking, regulatory, or
 * Legacy-sourced rule — configurable via application properties rather than hardcoded, and every
 * result produced from them carries the disclaimer in {@link #DISCLAIMER}. Deliberately NOT built
 * as a weighted-rule/points engine (unlike com.brika.platform.scoring, ADR-SCORING-001): that
 * engine is a closed, ADR-gated, multi-rule points system designed for property/operation criteria
 * unrelated to income, and reusing or extending it here would recreate exactly the "opaque complex
 * point system" this sprint is explicitly told to avoid for a single, well-defined ratio.
 */
@Component
public class ViabilityClassifier {

  public static final String DISCLAIMER =
      "Regla orientativa interna de Brikka V1. No representa un criterio oficial ni garantiza la"
          + " aprobación por una entidad financiera.";

  /**
   * Bumped only if the threshold values themselves change — persisted on every result so a past
   * result's classification stays reproducible even if the configured thresholds change later.
   */
  public static final String RULES_VERSION = "brikka-dti-v1";

  private static final int PERCENT_SCALE = 2;

  private final BigDecimal favorableMaxPercent;
  private final BigDecimal revisarMaxPercent;

  public ViabilityClassifier(
      @Value("${brika.financial-analysis.dti-favorable-max-percent:35}")
          BigDecimal favorableMaxPercent,
      @Value("${brika.financial-analysis.dti-revisar-max-percent:40}")
          BigDecimal revisarMaxPercent) {
    this.favorableMaxPercent = favorableMaxPercent;
    this.revisarMaxPercent = revisarMaxPercent;
  }

  /**
   * DTI = (existingMonthlyDebts + monthlyPayment) / monthlyIncome * 100. Caller must have already
   * validated monthlyIncome &gt; 0 — this never divides by zero.
   */
  public BigDecimal computeDti(
      BigDecimal monthlyIncome, BigDecimal existingMonthlyDebts, BigDecimal monthlyPayment) {
    BigDecimal totalMonthlyDebt = existingMonthlyDebts.add(monthlyPayment);
    return totalMonthlyDebt
        .multiply(BigDecimal.valueOf(100))
        .divide(monthlyIncome, PERCENT_SCALE, RoundingMode.HALF_UP);
  }

  public String classify(BigDecimal dtiPercent) {
    if (dtiPercent.compareTo(favorableMaxPercent) <= 0) {
      return "FAVORABLE";
    }
    if (dtiPercent.compareTo(revisarMaxPercent) <= 0) {
      return "REVISAR";
    }
    return "NO_VIABLE";
  }

  public BigDecimal favorableMaxPercent() {
    return favorableMaxPercent;
  }

  public BigDecimal revisarMaxPercent() {
    return revisarMaxPercent;
  }
}
