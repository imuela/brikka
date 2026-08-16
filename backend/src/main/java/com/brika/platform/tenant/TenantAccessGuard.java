package com.brika.platform.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Structural proof that SUPERADMIN cannot reach tenant-owned resources without SUPPORT_SESSION
 * (06_SECURITY_SPECIFICATION.md §3.1B): any access path gated by this guard fails whenever
 * TenantContext resolves no tenant, and SUPPORT_SESSION does not exist yet to supply one.
 */
public final class TenantAccessGuard {

  private TenantAccessGuard() {}

  public static UUID requireTenant(Optional<UUID> tenantId) {
    return tenantId.orElseThrow(
        () ->
            new NoActiveTenantException(
                "No active tenant: access to tenant-owned resources denied."));
  }
}
