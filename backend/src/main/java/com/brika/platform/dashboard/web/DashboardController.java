package com.brika.platform.dashboard.web;

import com.brika.platform.activity.Activity;
import com.brika.platform.activity.ActivityRepository;
import com.brika.platform.casemgmt.web.ActivityResponse;
import com.brika.platform.dashboard.DashboardRepository;
import com.brika.platform.dashboard.DashboardRepository.DashboardScope;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRole;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 27, Bloque 2 (FUNCTIONAL_SPECIFICATION.md §3): role-aware operational dashboard. Requires
 * ACTIVITY_READ (the same permission as the existing /api/v1/activities dashboard feed). A GLOBAL
 * SUPERADMIN (ADR-RBAC-002) sees company-wide totals; a BROKER sees only cases they are actively
 * assigned to.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

  private final AuthorizationService authorizationService;
  private final DashboardRepository dashboardRepository;
  private final ActivityRepository activityRepository;

  public DashboardController(
      AuthorizationService authorizationService,
      DashboardRepository dashboardRepository,
      ActivityRepository activityRepository) {
    this.authorizationService = authorizationService;
    this.dashboardRepository = dashboardRepository;
    this.activityRepository = activityRepository;
  }

  @GetMapping
  public DashboardResponse get(Authentication authentication) {
    authorizationService.requirePermission(authentication, "ACTIVITY_READ");
    User user = authorizationService.currentUser(authentication);

    DashboardScope scope;
    List<Activity> recent;
    if (authorizationService.isSuperadmin(authentication)) {
      scope = new DashboardScope(null, null, false);
      recent = activityRepository.findAll();
    } else {
      UUID tenantId = authorizationService.requireTenant(authentication);
      boolean broker = user.role() == UserRole.BROKER;
      scope = new DashboardScope(tenantId, user.id(), broker);
      recent =
          broker
              ? activityRepository.findAllAssignedToUser(tenantId, user.id())
              : activityRepository.findAllByCompanyId(tenantId);
    }

    return new DashboardResponse(
        dashboardRepository.countActiveCases(scope),
        dashboardRepository.countCasesByStatus(scope),
        dashboardRepository.countPendingTasks(scope),
        dashboardRepository.countOverdueTasks(scope),
        dashboardRepository.countPendingDocumentRequests(scope),
        recent.stream().limit(10).map(ActivityResponse::from).toList());
  }
}
