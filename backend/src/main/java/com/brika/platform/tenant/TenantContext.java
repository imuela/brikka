package com.brika.platform.tenant;

import com.brika.platform.identity.UserRole;
import java.util.Optional;
import java.util.UUID;

/**
 * Pure tenant resolution (ADR-IDENTITY-001, 06_SECURITY_SPECIFICATION.md §3.1B). SUPERADMIN never
 * resolves a tenant outside an active SUPPORT_SESSION; since SUPPORT_SESSION is not implemented
 * yet, SUPERADMIN always resolves to "no tenant" here. Wiring this against the real authenticated
 * principal (JWT/OIDC resource server) is Sprint 2 scope beyond this change.
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
