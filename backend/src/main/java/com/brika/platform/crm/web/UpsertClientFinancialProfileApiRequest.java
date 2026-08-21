package com.brika.platform.crm.web;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * source must be one of CLIENT/BROKER/AI (defaults to BROKER); status must be one of
 * PENDING/CONFIRMED/ESTIMATED/REJECTED/OUTDATED (defaults to PENDING).
 */
public record UpsertClientFinancialProfileApiRequest(
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
    UUID evidenceDocumentVersionId) {}
