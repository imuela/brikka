package com.brika.platform.casemgmt;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRole;
import com.brika.platform.security.AuthorizationService;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * First real implementation of "TENANT + ROLE/PERMISSION + CASE ASSIGNMENT" (ADR-RBAC-001): for
 * MANAGER the permission + tenant match is sufficient; for BROKER an active case_assignment row is
 * also required. A case in another tenant is masked as 404 (never confirms cross-tenant existence);
 * a case in the caller's own tenant that the BROKER is not assigned to is 403 (the resource exists
 * in their company, they simply lack case-level authorization for it).
 */
@Component
public class CaseAccessService {

  private final AuthorizationService authorizationService;
  private final CaseRepository caseRepository;
  private final CaseAssignmentRepository caseAssignmentRepository;

  public CaseAccessService(
      AuthorizationService authorizationService,
      CaseRepository caseRepository,
      CaseAssignmentRepository caseAssignmentRepository) {
    this.authorizationService = authorizationService;
    this.caseRepository = caseRepository;
    this.caseAssignmentRepository = caseAssignmentRepository;
  }

  public CaseAccessResult requireCaseAccess(
      Authentication authentication, String permissionCode, UUID caseId) {
    authorizationService.requirePermission(authentication, permissionCode);
    User user = authorizationService.currentUser(authentication);
    UUID tenantId = authorizationService.requireTenant(authentication);

    Case theCase =
        caseRepository
            .findById(caseId)
            .filter(c -> tenantId.equals(c.companyId()))
            .orElseThrow(() -> new ResourceNotFoundException("CASE_NOT_FOUND", "Case not found."));

    if (user.role() == UserRole.BROKER
        && !caseAssignmentRepository.hasActiveAssignment(caseId, user.id())) {
      throw new AccessDeniedException("No active case assignment for this case.");
    }

    return new CaseAccessResult(user, tenantId, theCase);
  }
}
