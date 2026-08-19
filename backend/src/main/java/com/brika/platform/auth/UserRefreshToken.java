package com.brika.platform.auth;

import java.time.Instant;
import java.util.UUID;

record UserRefreshToken(
    UUID id,
    UUID userId,
    UUID familyId,
    String tokenHash,
    Instant issuedAt,
    Instant expiresAt,
    Instant revokedAt,
    UUID replacedByTokenId) {}
