package com.brika.platform.tenant;

import com.brika.platform.identity.UserRole;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure tenant resolution (ADR-IDENTITY-001, 06_SECURITY_SPECIFICATION.md §3.1B) for the caller's
 * own company — SUPERADMIN has none, so this always resolves to "no tenant" for that role. This is
 * unrelated to whether SUPERADMIN can act on a specific tenant-owned resource: for reads and the
 * writes covered by Sprint 27 (ADR-RBAC-002), the tenant is resolved from the target resource
 * instead, deliberately never through this method. SUPPORT_SESSION (06_SECURITY_SPECIFICATION.md
 * §3.1B) remains the only path for the tenant-owned writes ADR-RBAC-002 left unaddressed, and is
 * still not implemented.
 */
public final class TenantContext {

  private TenantContext() {}

  public static Optional<UUID> resolve(UserRole role, UUID companyId) {
    if (role == UserRole.SUPERADMIN) {
      return Optional.empty();
    }
    return Optional.ofNullable(companyId);
  }
}
