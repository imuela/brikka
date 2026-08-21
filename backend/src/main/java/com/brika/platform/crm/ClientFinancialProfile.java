package com.brika.platform.crm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 30. Structural financial profile of a Client — reusable across Cases, distinct from an
 * operation's own financial data (requested amount, term, etc. on FinancingRequest/Simulation).
 * Fields taken 1:1 from the Brikka Legacy field analysis (clients/create.php); "company" (employer)
 * renamed to employerName to avoid colliding with the tenant Company concept. source/status/
 * evidenceDocumentVersionId satisfy the provenance/traceability requirements of
 * 03_DOMAIN_SPECIFICATION.md §5 and 07_DATA_GOVERNANCE_SPECIFICATION.md §2/§3/§6/§8 — status values
 * are stored as declared by the caller; no automatic transition logic (e.g. computing OUTDATED) is
 * implemented in Sprint 30.
 */
public record ClientFinancialProfile(
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
    Instant updatedAt) {}
