package com.brika.platform.identity.web;

import com.brika.platform.identity.PermissionResolutionService;
import com.brika.platform.identity.User;
import com.brika.platform.plan.EntitlementResolutionService;
import com.brika.platform.security.AuthorizationService;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §4. No RBAC permission required: every authenticated user may
 * read their own identity/permissions.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

  private final AuthorizationService authorizationService;
  private final PermissionResolutionService permissionResolutionService;
  private final EntitlementResolutionService entitlementResolutionService;

  public MeController(
      AuthorizationService authorizationService,
      PermissionResolutionService permissionResolutionService,
      EntitlementResolutionService entitlementResolutionService) {
    this.authorizationService = authorizationService;
    this.permissionResolutionService = permissionResolutionService;
    this.entitlementResolutionService = entitlementResolutionService;
  }

  @GetMapping
  public MeResponse me(Authentication authentication) {
    User user = authorizationService.currentUser(authentication);
    Map<String, String> entitlements =
        user.companyId() != null
            ? entitlementResolutionService.entitlementValuesForCompany(user.companyId())
            : Map.of();
    return new MeResponse(
        user.id(), user.email(), user.role().name(), user.companyId(), entitlements);
  }

  @GetMapping("/permissions")
  public PermissionsResponse permissions(Authentication authentication) {
    User user = authorizationService.currentUser(authentication);
    return new PermissionsResponse(permissionResolutionService.permissionCodesForUser(user.id()));
  }
}
