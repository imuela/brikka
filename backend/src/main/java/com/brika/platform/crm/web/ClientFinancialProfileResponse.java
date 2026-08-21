package com.brika.platform.crm.web;

import com.brika.platform.crm.ClientFinancialProfile;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClientFinancialProfileResponse(
    UUID id,
    UUID companyId,
    UUID clientId,
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
    UUID updatedBy,
    Instant createdAt,
    Instant updatedAt) {

  public static ClientFinancialProfileResponse from(ClientFinancialProfile profile) {
    return new ClientFinancialProfileResponse(
        profile.id(),
        profile.companyId(),
        profile.clientId(),
        profile.maritalStatus(),
        profile.dependents(),
        profile.employmentType(),
        profile.contractType(),
        profile.employerName(),
        profile.yearsEmployed(),
        profile.monthlyIncome(),
        profile.savings(),
        profile.otherDebtsMonthlyPayment(),
        profile.creditCardDebt(),
        profile.source(),
        profile.status(),
        profile.evidenceDocumentVersionId(),
        profile.updatedBy(),
        profile.createdAt(),
        profile.updatedAt());
  }
}
