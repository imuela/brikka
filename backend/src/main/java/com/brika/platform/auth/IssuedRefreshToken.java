package com.brika.platform.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * The raw (unhashed) refresh token value returned to the client exactly once, at issuance. {@code
 * ownerId} is the internal {@code users.id} or {@code client_portal_accounts.id} depending on which
 * service issued it — a plain data carrier, not an identity resolution.
 */
public record IssuedRefreshToken(String rawToken, UUID ownerId, UUID familyId, Instant expiresAt) {}
