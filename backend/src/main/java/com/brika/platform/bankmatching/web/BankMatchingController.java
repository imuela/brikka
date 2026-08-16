package com.brika.platform.bankmatching.web;

import com.brika.platform.bankmatching.BankMatchResult;
import com.brika.platform.bankmatching.BankMatchResultRepository;
import com.brika.platform.bankmatching.BankMatchRuleResult;
import com.brika.platform.bankmatching.BankMatchRuleResultRepository;
import com.brika.platform.bankmatching.BankMatchingService;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-BANKENGINE-001 §12 (D-C: pre-submission matching, not tied to a bank_request). TENANT +
 * ROLE/PERMISSION + CASE ASSIGNMENT via CaseAccessService, exactly as every other case-scoped
 * resource since Sprint 3. The snapshot is always built server-side (BankMatchingService) — never
 * accepted in the request body.
 */
@RestController
public class BankMatchingController {

  private final CaseAccessService caseAccessService;
  private final BankMatchingService bankMatchingService;
  private final BankMatchResultRepository bankMatchResultRepository;
  private final BankMatchRuleResultRepository bankMatchRuleResultRepository;
  private final ObjectMapper objectMapper;

  public BankMatchingController(
      CaseAccessService caseAccessService,
      BankMatchingService bankMatchingService,
      BankMatchResultRepository bankMatchResultRepository,
      BankMatchRuleResultRepository bankMatchRuleResultRepository,
      ObjectMapper objectMapper) {
    this.caseAccessService = caseAccessService;
    this.bankMatchingService = bankMatchingService;
    this.bankMatchResultRepository = bankMatchResultRepository;
    this.bankMatchRuleResultRepository = bankMatchRuleResultRepository;
    this.objectMapper = objectMapper;
  }

  @PostMapping("/api/v1/cases/{caseId}/banks/{bankId}/matching")
  public BankMatchResultResponse run(
      Authentication authentication, @PathVariable UUID caseId, @PathVariable UUID bankId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "BANK_MATCHING_RUN", caseId);
    BankMatchResult result =
        bankMatchingService.run(
            access.tenantId(), access.theCase().id(), bankId, access.user().id());
    return toResponse(result);
  }

  @GetMapping("/api/v1/cases/{caseId}/matching")
  public List<BankMatchResultResponse> list(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "BANK_MATCHING_READ", caseId);
    return bankMatchResultRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/api/v1/bank-match-results/{id}")
  public BankMatchResultResponse get(Authentication authentication, @PathVariable UUID id) {
    BankMatchResult result =
        bankMatchResultRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("BANK_MATCH_RESULT_NOT_FOUND", "Not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "BANK_MATCHING_READ", result.caseId());
    if (!result.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException("BANK_MATCH_RESULT_NOT_FOUND", "Not found.");
    }

    return toResponse(result);
  }

  private BankMatchResultResponse toResponse(BankMatchResult result) {
    List<RuleResultResponse> ruleResults =
        bankMatchRuleResultRepository.findAllByMatchResultId(result.id()).stream()
            .map(this::toResponse)
            .toList();
    return new BankMatchResultResponse(
        result.id(),
        result.caseId(),
        result.bankId(),
        result.bankCriteriaVersionId(),
        result.globalResult(),
        result.evaluatedAt(),
        readJson(result.inputSnapshot()),
        ruleResults);
  }

  private RuleResultResponse toResponse(BankMatchRuleResult ruleResult) {
    return new RuleResultResponse(
        ruleResult.ruleId(),
        ruleResult.field(),
        ruleResult.operator(),
        readJson(ruleResult.expectedValue()),
        ruleResult.evaluatedValue() == null ? null : readJson(ruleResult.evaluatedValue()),
        ruleResult.result(),
        ruleResult.reason());
  }

  private Object readJson(String json) {
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
