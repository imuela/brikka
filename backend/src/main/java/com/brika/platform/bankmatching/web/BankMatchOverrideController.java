package com.brika.platform.bankmatching.web;

import com.brika.platform.bankmatching.BankMatchOverrideService;
import com.brika.platform.bankmatching.BankMatchResult;
import com.brika.platform.bankmatching.BankMatchResultRepository;
import com.brika.platform.bankmatching.BankMatchRuleOverride;
import com.brika.platform.bankmatching.BankMatchRuleResult;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADR-BANKENGINE-002: manual correction of a single bank_match_rule_results row. Three-hop derived
 * access (ruleResult -> matchResult -> case), same masking discipline as BankOfferController's
 * two-hop pattern in Sprint 6A — a rule result belonging to another tenant's case is indistinguish-
 * able from one that never existed. MANAGER (CASE ASSIGNMENT via CaseAccessService) and SUPERADMIN
 * (SUPPORT_SESSION only, enforced by the same requireTenant pipeline as every other tenant-scoped
 * write) may override; BROKER and CLIENT never get BANK_MATCHING_OVERRIDE (V14).
 */
@RestController
public class BankMatchOverrideController {

  private final CaseAccessService caseAccessService;
  private final BankMatchOverrideService bankMatchOverrideService;
  private final BankMatchResultRepository bankMatchResultRepository;

  public BankMatchOverrideController(
      CaseAccessService caseAccessService,
      BankMatchOverrideService bankMatchOverrideService,
      BankMatchResultRepository bankMatchResultRepository) {
    this.caseAccessService = caseAccessService;
    this.bankMatchOverrideService = bankMatchOverrideService;
    this.bankMatchResultRepository = bankMatchResultRepository;
  }

  @PostMapping("/api/v1/bank-match-rule-results/{ruleResultId}/overrides")
  public BankMatchRuleOverrideResponse create(
      Authentication authentication,
      @PathVariable UUID ruleResultId,
      @RequestBody CreateBankMatchRuleOverrideApiRequest request) {
    BankMatchRuleResult ruleResult = bankMatchOverrideService.requireRuleResult(ruleResultId);
    BankMatchResult matchResult =
        bankMatchResultRepository
            .findById(ruleResult.matchResultId())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "BANK_MATCH_RULE_RESULT_NOT_FOUND", "Bank match rule result not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(
            authentication, "BANK_MATCHING_OVERRIDE", matchResult.caseId());
    if (!matchResult.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException(
          "BANK_MATCH_RULE_RESULT_NOT_FOUND", "Bank match rule result not found.");
    }

    BankMatchRuleOverride override =
        bankMatchOverrideService.create(
            access.tenantId(),
            ruleResultId,
            request.previousResult(),
            request.newResult(),
            request.reason(),
            access.user().id());

    return toResponse(override);
  }

  private BankMatchRuleOverrideResponse toResponse(BankMatchRuleOverride override) {
    return new BankMatchRuleOverrideResponse(
        override.id(),
        override.previousResult(),
        override.newResult(),
        override.reason(),
        override.overriddenBy(),
        override.overriddenAt());
  }
}
