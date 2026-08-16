package com.brika.platform.casemgmt.web;

import com.brika.platform.activity.Activity;
import com.brika.platform.activity.ActivityRepository;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRole;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §17B: authorization uses ACTIVITY_READ, not AUDIT_READ
 * (ADR-AUDIT-001), but still respects CASE_READ-equivalent scope (CASE ASSIGNMENT for BROKER).
 */
@RestController
public class ActivityController {

  private final AuthorizationService authorizationService;
  private final CaseAccessService caseAccessService;
  private final ActivityRepository activityRepository;

  public ActivityController(
      AuthorizationService authorizationService,
      CaseAccessService caseAccessService,
      ActivityRepository activityRepository) {
    this.authorizationService = authorizationService;
    this.caseAccessService = caseAccessService;
    this.activityRepository = activityRepository;
  }

  @GetMapping("/api/v1/cases/{caseId}/activities")
  public List<ActivityResponse> forCase(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "ACTIVITY_READ", caseId);
    return activityRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(ActivityResponse::from)
        .toList();
  }

  @GetMapping("/api/v1/activities")
  public List<ActivityResponse> dashboard(Authentication authentication) {
    authorizationService.requirePermission(authentication, "ACTIVITY_READ");
    User user = authorizationService.currentUser(authentication);
    UUID tenantId = authorizationService.requireTenant(authentication);
    List<Activity> activities =
        user.role() == UserRole.BROKER
            ? activityRepository.findAllAssignedToUser(tenantId, user.id())
            : activityRepository.findAllByCompanyId(tenantId);
    return activities.stream().map(ActivityResponse::from).toList();
  }
}
