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
 * never grants access: {@link #requireTenant} resolves the caller's own tenant and returns empty
 * for SUPERADMIN (who has none), so a write gated purely by requireTenant is denied for SUPERADMIN
 * unless the controller branches on {@link #isSuperadmin} first and resolves the tenant from the
 * target resource instead (Sprint 27, ADR-RBAC-002) — see that method's javadoc.
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
   * Requires the caller's own resolved tenant and returns it — always empty for SUPERADMIN (who has
   * no company of their own), so this throws for SUPERADMIN callers. Tenant-owned writes that
   * SUPERADMIN must still be able to perform (Sprint 27, ADR-RBAC-002) resolve the tenant from the
   * target resource instead, via an {@link #isSuperadmin} branch in the controller — this method is
   * only appropriate for the caller's own tenant, never the resource's.
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
