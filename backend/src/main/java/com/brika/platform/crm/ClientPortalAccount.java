package com.brika.platform.crm;

import java.time.Instant;
import java.util.UUID;

/** ADR-PORTAL-AUTH-001: the Portal authentication principal (never a `User`). */
public record ClientPortalAccount(
    UUID id,
    UUID companyId,
    UUID clientId,
    String externalIdentityId,
    String status,
    Instant lastLoginAt) {}
