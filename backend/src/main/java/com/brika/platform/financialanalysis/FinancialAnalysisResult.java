package com.brika.platform.financialanalysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 31. Append-only, one row per client analyzed in a single run — see V23 migration comment
 * for why (Case-scoped, no cross-client income aggregation).
 */
public record FinancialAnalysisResult(
    UUID id,
    UUID companyId,
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
    String explanationJson,
    UUID calculatedBy,
    Instant calculatedAt) {}
