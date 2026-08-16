package com.brika.platform.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brika.platform.identity.UserRole;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure unit tests for the ADR-IDENTITY-001 tenant resolution rule; no database needed. */
class TenantContextTest {

  @Test
  void superadminResolvesNoTenantRegardlessOfCompanyId() {
    assertThat(TenantContext.resolve(UserRole.SUPERADMIN, null)).isEmpty();
  }

  @Test
  void managerBrokerClientResolveTheirOwnCompanyId() {
    UUID companyId = UUID.randomUUID();
    assertThat(TenantContext.resolve(UserRole.MANAGER, companyId)).contains(companyId);
    assertThat(TenantContext.resolve(UserRole.BROKER, companyId)).contains(companyId);
    assertThat(TenantContext.resolve(UserRole.CLIENT, companyId)).contains(companyId);
  }

  @Test
  void superadminWithoutSupportSessionCannotAccessTenantOwnedResources() {
    Optional<UUID> tenantId = TenantContext.resolve(UserRole.SUPERADMIN, null);
    assertThatThrownBy(() -> TenantAccessGuard.requireTenant(tenantId))
        .isInstanceOf(NoActiveTenantException.class);
  }
}
