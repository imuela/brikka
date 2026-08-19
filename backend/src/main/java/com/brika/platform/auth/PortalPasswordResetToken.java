package com.brika.platform.auth;

import java.time.Instant;
import java.util.UUID;

record PortalPasswordResetToken(
    UUID id, UUID portalAccountId, String tokenHash, Instant expiresAt, Instant usedAt) {}
