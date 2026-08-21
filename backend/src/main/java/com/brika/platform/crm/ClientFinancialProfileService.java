package com.brika.platform.crm;

import com.brika.platform.common.error.ValidationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 30. Validates and persists the structural financial profile of a Client, writing an
 * append-only history snapshot on every write (07_DATA_GOVERNANCE_SPECIFICATION.md §4). Business
 * rules implemented here are limited to data-integrity validation (non-negative amounts, closed
 * source/status catalogs, tenant-safe evidence linking) — no scoring, DTI, or amortization logic;
 * those are explicitly out of scope for Sprint 30.
 */
@Service
public class ClientFinancialProfileService {

  private static final Set<String> VALID_SOURCES = Set.of("CLIENT", "BROKER", "AI");
  private static final Set<String> VALID_STATUSES =
      Set.of("PENDING", "CONFIRMED", "ESTIMATED", "REJECTED", "OUTDATED");

  private final ClientFinancialProfileRepository repository;
  private final ClientFinancialProfileHistoryRepository historyRepository;

  public ClientFinancialProfileService(
      ClientFinancialProfileRepository repository,
      ClientFinancialProfileHistoryRepository historyRepository) {
    this.repository = repository;
    this.historyRepository = historyRepository;
  }

  public Optional<ClientFinancialProfile> find(UUID clientId) {
    return repository.findByClientId(clientId);
  }

  public List<ClientFinancialProfileHistoryEntry> history(UUID clientId) {
    return historyRepository.findAllByClientId(clientId);
  }

  @Transactional
  public ClientFinancialProfile upsert(
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
      UUID actorUserId) {
    String resolvedSource = source == null || source.isBlank() ? "BROKER" : source;
    String resolvedStatus = status == null || status.isBlank() ? "PENDING" : status;
    validate(
        dependents,
        yearsEmployed,
        monthlyIncome,
        savings,
        otherDebtsMonthlyPayment,
        creditCardDebt,
        resolvedSource,
        resolvedStatus);
    requireEvidenceInSameTenant(companyId, evidenceDocumentVersionId);

    Optional<ClientFinancialProfile> existing = repository.findByClientId(clientId);
    if (existing.isPresent()) {
      repository.update(
          existing.get().id(),
          maritalStatus,
          dependents,
          employmentType,
          contractType,
          employerName,
          yearsEmployed,
          monthlyIncome,
          savings,
          otherDebtsMonthlyPayment,
          creditCardDebt,
          resolvedSource,
          resolvedStatus,
          evidenceDocumentVersionId,
          actorUserId);
    } else {
      repository.insert(
          companyId,
          clientId,
          maritalStatus,
          dependents,
          employmentType,
          contractType,
          employerName,
          yearsEmployed,
          monthlyIncome,
          savings,
          otherDebtsMonthlyPayment,
          creditCardDebt,
          resolvedSource,
          resolvedStatus,
          evidenceDocumentVersionId,
          actorUserId);
    }

    ClientFinancialProfile saved = repository.findByClientId(clientId).orElseThrow();
    historyRepository.insert(saved, actorUserId);
    return saved;
  }

  private void validate(
      Integer dependents,
      Integer yearsEmployed,
      BigDecimal monthlyIncome,
      BigDecimal savings,
      BigDecimal otherDebtsMonthlyPayment,
      BigDecimal creditCardDebt,
      String source,
      String status) {
    if (!VALID_SOURCES.contains(source)) {
      throw new ValidationException(
          "INVALID_FINANCIAL_PROFILE_SOURCE", "source must be one of " + VALID_SOURCES + ".");
    }
    if (!VALID_STATUSES.contains(status)) {
      throw new ValidationException(
          "INVALID_FINANCIAL_PROFILE_STATUS", "status must be one of " + VALID_STATUSES + ".");
    }
    requireNonNegative("dependents", dependents == null ? null : BigDecimal.valueOf(dependents));
    requireNonNegative(
        "yearsEmployed", yearsEmployed == null ? null : BigDecimal.valueOf(yearsEmployed));
    requireNonNegative("monthlyIncome", monthlyIncome);
    requireNonNegative("savings", savings);
    requireNonNegative("otherDebtsMonthlyPayment", otherDebtsMonthlyPayment);
    requireNonNegative("creditCardDebt", creditCardDebt);
  }

  private void requireNonNegative(String field, BigDecimal value) {
    if (value != null && value.signum() < 0) {
      throw new ValidationException("NEGATIVE_FINANCIAL_VALUE", field + " must not be negative.");
    }
  }

  /**
   * 07_DATA_GOVERNANCE_SPECIFICATION.md §3: evidence must exist and, since the profile is a
   * client-level (not case-level) resource, must belong to the same tenant as the profile itself —
   * a bare FK alone would let evidence from another company's case be linked.
   */
  private void requireEvidenceInSameTenant(UUID companyId, UUID evidenceDocumentVersionId) {
    if (evidenceDocumentVersionId == null) {
      return;
    }
    UUID evidenceCompanyId =
        repository
            .resolveDocumentVersionCompanyId(evidenceDocumentVersionId)
            .orElseThrow(
                () ->
                    new ValidationException(
                        "EVIDENCE_DOCUMENT_VERSION_NOT_FOUND",
                        "evidenceDocumentVersionId does not reference an existing document"
                            + " version."));
    if (!companyId.equals(evidenceCompanyId)) {
      throw new ValidationException(
          "EVIDENCE_DOCUMENT_VERSION_NOT_FOUND",
          "evidenceDocumentVersionId does not reference an existing document version.");
    }
  }
}
