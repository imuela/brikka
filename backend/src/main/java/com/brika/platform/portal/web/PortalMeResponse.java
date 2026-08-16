package com.brika.platform.portal.web;

import java.time.Instant;
import java.util.UUID;

public record PortalMeResponse(
    UUID clientId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String accountStatus,
    Instant lastLoginAt) {}
