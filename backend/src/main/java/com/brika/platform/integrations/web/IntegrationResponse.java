package com.brika.platform.integrations.web;

import java.time.Instant;
import java.util.UUID;

public record IntegrationResponse(
    UUID id,
    UUID companyId,
    String type,
    String status,
    Object config,
    Instant createdAt,
    Instant updatedAt) {}
