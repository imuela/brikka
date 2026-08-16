package com.brika.platform.financing.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.financing.FinancingRequest;
import com.brika.platform.financing.FinancingRequestRepository;
import com.brika.platform.financing.FinancingRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 5 pre-flight decision 11.1 (API contract was not documented anywhere for
 * financing_requests): GET/POST nested under the case, PATCH standalone. No DELETE. The standalone
 * PATCH derives case/tenant access from the loaded resource, mirroring
 * DocumentRequestController.update (Sprint 4).
 */
@RestController
public class FinancingRequestController {

  private final CaseAccessService caseAccessService;
  private final FinancingRequestRepository financingRequestRepository;

  public FinancingRequestController(
      CaseAccessService caseAccessService, FinancingRequestRepository financingRequestRepository) {
    this.caseAccessService = caseAccessService;
    this.financingRequestRepository = financingRequestRepository;
  }

  @GetMapping("/api/v1/cases/{caseId}/financing-requests")
  public List<FinancingRequestResponse> list(
      Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "FINANCING_REQUEST_READ", caseId);
    return financingRequestRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(FinancingRequestController::toResponse)
        .toList();
  }

  @PostMapping("/api/v1/cases/{caseId}/financing-requests")
  public FinancingRequestResponse create(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody CreateFinancingRequestApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "FINANCING_REQUEST_CREATE", caseId);
    UUID id =
        financingRequestRepository.insert(
            access.tenantId(),
            access.theCase().id(),
            request.requestedAmount(),
            request.termMonths());
    return toResponse(financingRequestRepository.findById(id).orElseThrow());
  }

  @PatchMapping("/api/v1/financing-requests/{id}")
  public FinancingRequestResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateFinancingRequestApiRequest request) {
    FinancingRequest financingRequest =
        financingRequestRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("FINANCING_REQUEST_NOT_FOUND", "Not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(
            authentication, "FINANCING_REQUEST_UPDATE", financingRequest.caseId());
    if (!financingRequest.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException("FINANCING_REQUEST_NOT_FOUND", "Not found.");
    }

    FinancingRequestStatus status = parseStatus(request.status());
    financingRequestRepository.update(
        id, status.name(), request.requestedAmount(), request.termMonths());
    return toResponse(financingRequestRepository.findById(id).orElseThrow());
  }

  private FinancingRequestStatus parseStatus(String value) {
    try {
      return FinancingRequestStatus.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("INVALID_STATUS", "Unknown financing request status: " + value);
    }
  }

  private static FinancingRequestResponse toResponse(FinancingRequest financingRequest) {
    return new FinancingRequestResponse(
        financingRequest.id(),
        financingRequest.caseId(),
        financingRequest.status(),
        financingRequest.requestedAmount(),
        financingRequest.termMonths(),
        financingRequest.createdAt(),
        financingRequest.updatedAt());
  }
}
