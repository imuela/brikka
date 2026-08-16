package com.brika.platform.identity;

import java.util.UUID;

/** companyId is null only for SUPERADMIN (ADR-IDENTITY-001). */
public record User(
    UUID id,
    UUID companyId,
    String email,
    String firstName,
    String lastName,
    String status,
    UserRole role) {}
