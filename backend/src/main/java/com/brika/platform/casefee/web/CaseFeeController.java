package com.brika.platform.casefee.web;

import com.brika.platform.audit.AuditEventWriter;
import com.brika.platform.casefee.CaseFee;
import com.brika.platform.casefee.CaseFeeService;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 32. Reuses CASE_READ/CASE_UPDATE — identical RBAC shape already required for honorarios
 * (SUPERADMIN/MANAGER/BROKER at case-assignment scope, CLIENT excluded), same reasoning as
 * ClientFinancialProfileController (Sprint 30) reusing CLIENT_READ/CLIENT_UPDATE: no approved
 * documentation calls for a stricter, dedicated permission. Case-scoped via CaseAccessService (not
 * the simpler tenant-only check used for Client), because a broker's fee visibility must be gated
 * by case assignment like every other case sub-resource.
 */
@RestController
public class CaseFeeController {

  private final CaseAccessService caseAccessService;
  private final CaseFeeService caseFeeService;
  private final AuditEventWriter auditEventWriter;

  public CaseFeeController(
      CaseAccessService caseAccessService,
      CaseFeeService caseFeeService,
      AuditEventWriter auditEventWriter) {
    this.caseAccessService = caseAccessService;
    this.caseFeeService = caseFeeService;
    this.auditEventWriter = auditEventWriter;
  }

  @GetMapping("/api/v1/cases/{caseId}/fee")
  public CaseFeeResponse get(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_READ", caseId);
    CaseFee fee =
        caseFeeService
            .find(access.theCase().id())
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "CASE_FEE_NOT_FOUND", "This case has no fee configured yet."));
    return CaseFeeResponse.from(fee);
  }

  @PutMapping("/api/v1/cases/{caseId}/fee")
  public CaseFeeResponse upsert(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody UpsertCaseFeeApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_UPDATE", caseId);
    UUID actorUserId = access.user().id();
    CaseFee saved =
        caseFeeService.upsert(
            access.tenantId(),
            access.theCase().id(),
            request.feeType(),
            request.fixedAmount(),
            request.percentage(),
            request.calculationBase(),
            request.status(),
            request.agreedAt(),
            actorUserId);
    // Sprint 12 D12-2 (ADR-AUDIT-002) precedent: only the case id is recorded, never the actual
    // fee values — reconstructible from case_fee_history, access-controlled the same way as the
    // fee itself.
    auditEventWriter.write(
        access.tenantId(),
        actorUserId,
        null,
        "CASE_FEE_UPDATED",
        "CASE",
        caseId,
        "{\"caseId\":\"" + caseId + "\"}");
    return CaseFeeResponse.from(saved);
  }

  @GetMapping("/api/v1/cases/{caseId}/fee/history")
  public List<CaseFeeHistoryResponse> history(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CASE_READ", caseId);
    return caseFeeService.history(access.theCase().id()).stream()
        .map(CaseFeeHistoryResponse::from)
        .toList();
  }
}
