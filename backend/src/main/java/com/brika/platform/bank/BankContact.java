package com.brika.platform.bank;

import java.time.Instant;
import java.util.UUID;

/**
 * 06_BANK_ENGINE_SPECIFICATION.md §3-5, ADR-BANK-001: BANK_CONTACT belongs to COMPANY (tenant), not
 * to the broker who created it. visibility distinguishes COMPANY (any tenant member) from PRIVATE
 * (owner + MANAGER only, per Sprint 5 pre-flight decision).
 */
public record BankContact(
    UUID id,
    UUID companyId,
    UUID bankId,
    UUID ownerUserId,
    String name,
    String position,
    String department,
    String branch,
    String email,
    String phone,
    String secondaryPhone,
    String notes,
    String visibility,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {}
