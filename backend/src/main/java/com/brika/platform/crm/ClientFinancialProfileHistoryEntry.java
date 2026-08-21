package com.brika.platform.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 30. Append-only snapshot written on every write to ClientFinancialProfile — same
 * "reconstruct how a value was reached" purpose as CaseStatusHistory, satisfying
 * 07_DATA_GOVERNANCE_SPECIFICATION.md §4 ("historial").
 */
public record ClientFinancialProfileHistoryEntry(
    UUID id,
    UUID companyId,
    UUID clientId,
    UUID financialProfileId,
    String maritalStatus,
    Integer dependents,
    String employmentType,
    String contractType,
    String employerName,
    Integer yearsEmployed,
    BigDecimal monthlyIncome,
    BigDecimal savings,
    BigDecimal otherDebtsMonthlyPayment,
    BigDecimal creditCardDebt,
    String source,
    String status,
    UUID evidenceDocumentVersionId,
    UUID changedBy,
    Instant changedAt) {}
