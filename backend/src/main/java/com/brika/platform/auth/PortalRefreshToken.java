package com.brika.platform.auth;

import java.time.Instant;
import java.util.UUID;

record PortalRefreshToken(
    UUID id,
    UUID portalAccountId,
    UUID familyId,
    String tokenHash,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt,
    UUID replacedByTokenId) {}
