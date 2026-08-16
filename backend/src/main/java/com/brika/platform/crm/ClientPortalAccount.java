package com.brika.platform.crm;

import java.time.Instant;
import java.util.UUID;

/**
 * Model/repository only in Sprint 3 (approved scope: no REST endpoint, no auth wiring). Real Portal
 * Cliente authentication/activation is Sprint 7.
 */
public record ClientPortalAccount(
    UUID id,
    UUID companyId,
    UUID clientId,
    String externalIdentityId,
    String status,
    Instant lastLoginAt) {}
