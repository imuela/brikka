package com.brika.platform.bankmatching;

import com.brika.platform.common.error.ConflictException;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-BANKENGINE-002: creates immutable, append-only corrections of a single bank_match_rule_result
 * and derives the effective per-rule / global result at read time. Never mutates bank_match_results
 * or bank_match_rule_results (D3/§11 of ADR-BANKENGINE-001, restated in ADR-BANKENGINE-002 §5).
 */
@Service
public class BankMatchOverrideService {

  private static final Set<MatchResult> OVERRIDABLE_RESULTS =
      EnumSet.of(
          MatchResult.PASS, MatchResult.FAIL, MatchResult.WARNING, MatchResult.NOT_EVALUATED);

  private final BankMatchRuleResultRepository bankMatchRuleResultRepository;
  private final BankMatchRuleOverrideRepository bankMatchRuleOverrideRepository;
  private final MatchingEngine matchingEngine;

  public BankMatchOverrideService(
      BankMatchRuleResultRepository bankMatchRuleResultRepository,
      BankMatchRuleOverrideRepository bankMatchRuleOverrideRepository,
      MatchingEngine matchingEngine) {
    this.bankMatchRuleResultRepository = bankMatchRuleResultRepository;
    this.bankMatchRuleOverrideRepository = bankMatchRuleOverrideRepository;
    this.matchingEngine = matchingEngine;
  }

  public BankMatchRuleResult requireRuleResult(UUID ruleResultId) {
    return bankMatchRuleResultRepository
        .findById(ruleResultId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "BANK_MATCH_RULE_RESULT_NOT_FOUND", "Bank match rule result not found."));
  }

  /** ADR-BANKENGINE-002 §2: full history, oldest first. */
  public List<BankMatchRuleOverride> historyForRuleResult(UUID ruleResultId) {
    return bankMatchRuleOverrideRepository.findAllByRuleResultId(ruleResultId);
  }

  /** ADR-BANKENGINE-002 §3: the most recent override wins; otherwise the original result stands. */
  public MatchResult effectiveResult(
      BankMatchRuleResult ruleResult, List<BankMatchRuleOverride> history) {
    if (history.isEmpty()) {
      return MatchResult.valueOf(ruleResult.result());
    }
    return MatchResult.valueOf(history.get(history.size() - 1).newResult());
  }

  /**
   * ADR-BANKENGINE-002 §3: global effective result is always derived, never stored. A match result
   * with no rule results (the ERROR defense-in-depth path — see BankMatchingService) has nothing to
   * derive from, so the original global result stands as-is.
   */
  public MatchResult effectiveGlobalResult(
      String originalGlobalResult, List<MatchResult> effectivePerRuleResults) {
    if (effectivePerRuleResults.isEmpty()) {
      return MatchResult.valueOf(originalGlobalResult);
    }
    return matchingEngine.aggregateResults(effectivePerRuleResults);
  }

  @Transactional
  public BankMatchRuleOverride create(
      UUID companyId,
      UUID ruleResultId,
      String previousResultRaw,
      String newResultRaw,
      String reason,
      UUID overriddenBy) {
    validateReason(reason);
    MatchResult previousResult = parseOverridableResult(previousResultRaw, "previousResult");
    MatchResult newResult = parseOverridableResult(newResultRaw, "newResult");
    if (previousResult == newResult) {
      throw new ValidationException(
          "OVERRIDE_NOOP", "newResult must be different from previousResult.");
    }

    BankMatchRuleResult ruleResult = requireRuleResult(ruleResultId);
    List<BankMatchRuleOverride> history = historyForRuleResult(ruleResultId);
    MatchResult currentEffective = effectiveResult(ruleResult, history);
    if (currentEffective != previousResult) {
      throw new ConflictException(
          "OVERRIDE_STALE_PREVIOUS_RESULT",
          "previousResult does not match the current effective result ("
              + currentEffective
              + "). Reload and retry.");
    }

    UUID id =
        bankMatchRuleOverrideRepository.insert(
            companyId, ruleResultId, previousResult.name(), newResult.name(), reason, overriddenBy);
    return bankMatchRuleOverrideRepository.findById(id).orElseThrow();
  }

  private void validateReason(String reason) {
    if (reason == null || reason.isBlank() || reason.length() > 500) {
      throw new ValidationException(
          "INVALID_OVERRIDE_REASON", "reason must be non-blank and at most 500 characters.");
    }
  }

  private MatchResult parseOverridableResult(String raw, String fieldName) {
    MatchResult result;
    try {
      result = MatchResult.valueOf(raw);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ValidationException(
          "INVALID_OVERRIDE_RESULT",
          fieldName + " must be one of PASS, FAIL, WARNING, NOT_EVALUATED.");
    }
    if (!OVERRIDABLE_RESULTS.contains(result)) {
      throw new ValidationException(
          "INVALID_OVERRIDE_RESULT",
          fieldName + " must be one of PASS, FAIL, WARNING, NOT_EVALUATED.");
    }
    return result;
  }
}
