package com.brika.platform.integrations;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-INTEGRATIONS-001: minimal extensibility structure, read/status only in V1, no adapter
 * execution. companyId is nullable in schema (V7) — an integration may be GLOBAL (not tied to any
 * single tenant) or company-scoped; V1 never writes rows, so this is a structural allowance only.
 */
public record Integration(
    UUID id,
    UUID companyId,
    String type,
    String status,
    String configJson,
    String credentialsRef,
    Instant createdAt,
    Instant updatedAt) {}
