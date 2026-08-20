package com.brika.platform.security;

import com.brika.platform.identity.PermissionResolutionService;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRole;
import com.brika.platform.tenant.NoActiveTenantException;
import com.brika.platform.tenant.TenantAccessGuard;
import com.brika.platform.tenant.TenantContext;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Ties the already-approved building blocks (PermissionResolutionService, TenantContext,
 * TenantAccessGuard) into the "tenant + role/permission + resource scope" pipeline required by
 * 06_SECURITY_SPECIFICATION.md §3.0 / 17_API_SPECIFICATION_DETAILED.md §21. A permission alone
 * never grants access: every write here also requires a resolved tenant, and for SUPERADMIN that
 * never happens without SUPPORT_SESSION (not implemented), so SUPERADMIN is denied in practice.
 */
@Component
public class AuthorizationService {

  private final PermissionResolutionService permissionResolutionService;

  public AuthorizationService(PermissionResolutionService permissionResolutionService) {
    this.permissionResolutionService = permissionResolutionService;
  }

  public User currentUser(Authentication authentication) {
    if (!(authentication instanceof BrikaAuthenticationToken token)) {
      throw new AccessDeniedException("No authenticated Brika user");
    }
    return token.user();
  }

  /**
   * Sprint 27 (ADR-RBAC-002): SUPERADMIN is the platform administrator. Tenant-scoped resources
   * resolve their tenant from the resource being accessed (see CaseAccessService), so this helper
   * is used by controllers to branch into the GLOBAL scope instead of requireTenant. It is never a
   * blanket "return true" bypass: permission checks still run, and tenant isolation among real
   * tenant users (MANAGER/BROKER/CLIENT) is unchanged.
   */
  public boolean isSuperadmin(Authentication authentication) {
    return currentUser(authentication).role() == UserRole.SUPERADMIN;
  }

  public void requirePermission(Authentication authentication, String permissionCode) {
    User user = currentUser(authentication);
    Set<String> permissions = permissionResolutionService.permissionCodesForUser(user.id());
    if (!permissions.contains(permissionCode)) {
      throw new AccessDeniedException("Missing permission: " + permissionCode);
    }
  }

  /**
   * Requires a resolved tenant (never true for SUPERADMIN without SUPPORT_SESSION) and returns it.
   */
  public UUID requireTenant(Authentication authentication) {
    User user = currentUser(authentication);
    try {
      return TenantAccessGuard.requireTenant(TenantContext.resolve(user.role(), user.companyId()));
    } catch (NoActiveTenantException e) {
      throw new AccessDeniedException(e.getMessage(), e);
    }
  }
}
