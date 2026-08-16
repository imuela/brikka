package com.brika.platform.bank;

import java.util.UUID;

/**
 * Global, unique catalog (06_BANK_ENGINE_SPECIFICATION.md §2) — never duplicated per
 * company/broker.
 */
public record Bank(UUID id, String code, String name, String status, String metadata) {}
