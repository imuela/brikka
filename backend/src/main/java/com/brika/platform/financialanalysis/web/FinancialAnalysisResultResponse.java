package com.brika.platform.financialanalysis.web;

import com.brika.platform.financialanalysis.FinancialAnalysisResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinancialAnalysisResultResponse(
    UUID id,
    UUID caseId,
    UUID clientId,
    BigDecimal principal,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal monthlyPayment,
    BigDecimal monthlyIncome,
    BigDecimal existingMonthlyDebts,
    BigDecimal dtiPercent,
    String viabilityCategory,
    String quotaSource,
    UUID quotaSourceId,
    String rulesVersion,
    Object explanation,
    UUID calculatedBy,
    Instant calculatedAt) {

  public static FinancialAnalysisResultResponse from(
      FinancialAnalysisResult result, Object explanation) {
    return new FinancialAnalysisResultResponse(
        result.id(),
        result.caseId(),
        result.clientId(),
        result.principal(),
        result.interestRate(),
        result.termMonths(),
        result.monthlyPayment(),
        result.monthlyIncome(),
        result.existingMonthlyDebts(),
        result.dtiPercent(),
        result.viabilityCategory(),
        result.quotaSource(),
        result.quotaSourceId(),
        result.rulesVersion(),
        explanation,
        result.calculatedBy(),
        result.calculatedAt());
  }
}
