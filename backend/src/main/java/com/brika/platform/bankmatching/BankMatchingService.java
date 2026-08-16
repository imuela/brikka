package com.brika.platform.bankmatching;

import com.brika.platform.bank.Bank;
import com.brika.platform.bank.BankCriteriaVersion;
import com.brika.platform.bank.BankCriteriaVersionRepository;
import com.brika.platform.bank.BankRepository;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates one matching execution end to end (ADR-BANKENGINE-001 §5-§10): loads the bank's
 * active criteria version, re-validates it (§8 defense in depth — write-time validation in
 * BankController already guarantees this passes in practice), builds the server-side snapshot (D-C
 * — never accepted from the client), evaluates, and persists an immutable result.
 */
@Service
public class BankMatchingService {

  private final BankRepository bankRepository;
  private final BankCriteriaVersionRepository bankCriteriaVersionRepository;
  private final CriteriaRulesValidator criteriaRulesValidator;
  private final InputSnapshotFactory inputSnapshotFactory;
  private final MatchingEngine matchingEngine;
  private final BankMatchResultRepository bankMatchResultRepository;
  private final BankMatchRuleResultRepository bankMatchRuleResultRepository;
  private final ObjectMapper objectMapper;

  public BankMatchingService(
      BankRepository bankRepository,
      BankCriteriaVersionRepository bankCriteriaVersionRepository,
      CriteriaRulesValidator criteriaRulesValidator,
      InputSnapshotFactory inputSnapshotFactory,
      MatchingEngine matchingEngine,
      BankMatchResultRepository bankMatchResultRepository,
      BankMatchRuleResultRepository bankMatchRuleResultRepository,
      ObjectMapper objectMapper) {
    this.bankRepository = bankRepository;
    this.bankCriteriaVersionRepository = bankCriteriaVersionRepository;
    this.criteriaRulesValidator = criteriaRulesValidator;
    this.inputSnapshotFactory = inputSnapshotFactory;
    this.matchingEngine = matchingEngine;
    this.bankMatchResultRepository = bankMatchResultRepository;
    this.bankMatchRuleResultRepository = bankMatchRuleResultRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public BankMatchResult run(UUID companyId, UUID caseId, UUID bankId, UUID evaluatedBy) {
    Bank bank =
        bankRepository
            .findById(bankId)
            .orElseThrow(() -> new ResourceNotFoundException("BANK_NOT_FOUND", "Bank not found."));

    BankCriteriaVersion criteriaVersion =
        bankCriteriaVersionRepository.findAllByBankId(bank.id()).stream()
            .filter(v -> "ACTIVE".equals(v.status()))
            .findFirst()
            .orElseThrow(
                () ->
                    new ValidationException(
                        "NO_ACTIVE_CRITERIA_VERSION",
                        "Bank has no ACTIVE criteria version to match against."));

    InputSnapshot snapshot = inputSnapshotFactory.build(caseId);
    String snapshotJson = writeSnapshot(snapshot);

    MatchingRuleSet ruleSet;
    try {
      ruleSet = criteriaRulesValidator.validate(criteriaVersion.rules());
    } catch (ValidationException e) {
      // §8: defense in depth against data corruption bypassing write-time validation. Never
      // reachable in practice — BankController validates before persisting — but the engine must
      // never silently skip an unrecognized rule and continue.
      UUID errorResultId =
          bankMatchResultRepository.insert(
              companyId,
              caseId,
              bank.id(),
              criteriaVersion.id(),
              MatchResult.ERROR.name(),
              snapshotJson,
              evaluatedBy);
      return bankMatchResultRepository.findById(errorResultId).orElseThrow();
    }

    EngineEvaluation evaluation = matchingEngine.evaluate(ruleSet, snapshot);

    UUID matchResultId =
        bankMatchResultRepository.insert(
            companyId,
            caseId,
            bank.id(),
            criteriaVersion.id(),
            evaluation.globalResult().name(),
            snapshotJson,
            evaluatedBy);

    for (RuleEvaluation ruleEvaluation : evaluation.ruleEvaluations()) {
      bankMatchRuleResultRepository.insert(
          matchResultId,
          ruleEvaluation.ruleId(),
          ruleEvaluation.field().jsonName(),
          ruleEvaluation.operator().name(),
          writeValue(ruleEvaluation.expectedValue()),
          writeScalar(ruleEvaluation.evaluatedValue()),
          ruleEvaluation.result().name(),
          ruleEvaluation.reason());
    }

    return bankMatchResultRepository.findById(matchResultId).orElseThrow();
  }

  private String writeSnapshot(InputSnapshot snapshot) {
    Map<String, Object> computed = new LinkedHashMap<>();
    computed.put("ltv", snapshot.ltv());
    Map<String, Object> financingRequest = new LinkedHashMap<>();
    financingRequest.put("requestedAmount", snapshot.requestedAmount());
    financingRequest.put("termMonths", snapshot.termMonths());
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("computed", computed);
    root.put("financingRequest", financingRequest);
    return toJson(root);
  }

  private String writeValue(MatchValue value) {
    return toJson(value.isArray() ? value.asArray() : value.asScalar());
  }

  private String writeScalar(BigDecimal value) {
    return value == null ? null : toJson(value);
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize matching value", e);
    }
  }
}
