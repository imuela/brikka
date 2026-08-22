package com.brika.platform.casefee;

import com.brika.platform.common.error.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint 32. Honorarios del broker para un caso. calculatedAmount siempre lo calcula el backend,
 * nunca se acepta del cliente HTTP: para FIXED es fixedAmount tal cual; para PERCENTAGE es
 * calculationBase * percentage / 100, BigDecimal, escala 2, HALF_UP — mismo redondeo monetario que
 * MortgagePaymentCalculator (Sprint 31). calculationBase es siempre un importe introducido
 * explícitamente por quien configura el honorario (ver V25): nunca se deriva automáticamente de
 * Case.requestedAmount, de una oferta bancaria ni de una simulación, para no encadenar una regla de
 * "qué importe prevalece" que ningún documento respalda.
 */
@Service
public class CaseFeeService {

  private static final Set<String> VALID_FEE_TYPES = Set.of("FIXED", "PERCENTAGE");
  private static final Set<String> VALID_STATUSES = Set.of("PROPOSED", "AGREED", "CANCELLED");
  private static final int MONEY_SCALE = 2;

  private final CaseFeeRepository repository;
  private final CaseFeeHistoryRepository historyRepository;

  public CaseFeeService(CaseFeeRepository repository, CaseFeeHistoryRepository historyRepository) {
    this.repository = repository;
    this.historyRepository = historyRepository;
  }

  public Optional<CaseFee> find(UUID caseId) {
    return repository.findByCaseId(caseId);
  }

  public List<CaseFeeHistoryEntry> history(UUID caseId) {
    return historyRepository.findAllByCaseId(caseId);
  }

  @Transactional
  public CaseFee upsert(
      UUID companyId,
      UUID caseId,
      String feeType,
      BigDecimal fixedAmount,
      BigDecimal percentage,
      BigDecimal calculationBase,
      String status,
      Instant agreedAt,
      UUID actorUserId) {
    String resolvedStatus = status == null || status.isBlank() ? "PROPOSED" : status;
    validate(feeType, fixedAmount, percentage, calculationBase, resolvedStatus);
    BigDecimal calculatedAmount =
        "FIXED".equals(feeType)
            ? fixedAmount.setScale(MONEY_SCALE, RoundingMode.HALF_UP)
            : calculationBase
                .multiply(percentage)
                .divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);

    Optional<CaseFee> existing = repository.findByCaseId(caseId);
    if (existing.isPresent()) {
      repository.update(
          existing.get().id(),
          feeType,
          fixedAmount,
          percentage,
          calculationBase,
          calculatedAmount,
          resolvedStatus,
          agreedAt,
          actorUserId);
    } else {
      repository.insert(
          companyId,
          caseId,
          feeType,
          fixedAmount,
          percentage,
          calculationBase,
          calculatedAmount,
          resolvedStatus,
          agreedAt,
          actorUserId);
    }

    CaseFee saved = repository.findByCaseId(caseId).orElseThrow();
    historyRepository.insert(saved, actorUserId);
    return saved;
  }

  private void validate(
      String feeType,
      BigDecimal fixedAmount,
      BigDecimal percentage,
      BigDecimal calculationBase,
      String status) {
    if (!VALID_FEE_TYPES.contains(feeType)) {
      throw new ValidationException(
          "INVALID_FEE_TYPE", "feeType must be one of " + VALID_FEE_TYPES + ".");
    }
    if (!VALID_STATUSES.contains(status)) {
      throw new ValidationException(
          "INVALID_FEE_STATUS", "status must be one of " + VALID_STATUSES + ".");
    }
    if ("FIXED".equals(feeType)) {
      if (fixedAmount == null) {
        throw new ValidationException(
            "FIXED_AMOUNT_REQUIRED", "fixedAmount is required when feeType is FIXED.");
      }
      requireNonNegative("fixedAmount", fixedAmount);
    } else {
      if (percentage == null) {
        throw new ValidationException(
            "PERCENTAGE_REQUIRED", "percentage is required when feeType is PERCENTAGE.");
      }
      if (calculationBase == null) {
        throw new ValidationException(
            "CALCULATION_BASE_REQUIRED", "calculationBase is required when feeType is PERCENTAGE.");
      }
      requireNonNegative("percentage", percentage);
      if (percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
        throw new ValidationException("INVALID_PERCENTAGE", "percentage must not exceed 100.");
      }
      requireNonNegative("calculationBase", calculationBase);
    }
  }

  private void requireNonNegative(String field, BigDecimal value) {
    if (value.signum() < 0) {
      throw new ValidationException("NEGATIVE_FEE_VALUE", field + " must not be negative.");
    }
  }
}
