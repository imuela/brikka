package com.brika.platform.auth;

import java.time.Instant;
import java.util.UUID;

record UserPasswordResetToken(
    UUID id, UUID userId, String tokenHash, Instant expiresAt, Instant usedAt) {}
