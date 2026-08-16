package com.brika.platform.identity.web;

import java.util.Map;
import java.util.UUID;

/**
 * 17_API_SPECIFICATION_DETAILED.md §4: entitlements are informational only (frontend hint), never a
 * substitute for the backend authorization check on the actual endpoint (ADR-PLATFORM-001).
 */
public record MeResponse(
    UUID id, String email, String role, UUID companyId, Map<String, String> entitlements) {}
