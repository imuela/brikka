package com.brika.platform.crm.web;

import com.brika.platform.crm.ClientFinancialProfileHistoryEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClientFinancialProfileHistoryResponse(
    UUID id,
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
    Instant changedAt) {

  public static ClientFinancialProfileHistoryResponse from(
      ClientFinancialProfileHistoryEntry entry) {
    return new ClientFinancialProfileHistoryResponse(
        entry.id(),
        entry.financialProfileId(),
        entry.maritalStatus(),
        entry.dependents(),
        entry.employmentType(),
        entry.contractType(),
        entry.employerName(),
        entry.yearsEmployed(),
        entry.monthlyIncome(),
        entry.savings(),
        entry.otherDebtsMonthlyPayment(),
        entry.creditCardDebt(),
        entry.source(),
        entry.status(),
        entry.evidenceDocumentVersionId(),
        entry.changedBy(),
        entry.changedAt());
  }
}
