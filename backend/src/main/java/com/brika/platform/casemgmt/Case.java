package com.brika.platform.casemgmt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 27, Bloque 4 (FUNCTIONAL_SPECIFICATION.md §7): requestedAmount/description are nullable
 * operation details captured at creation/editing — legacy creation with only operationType still
 * works. requestedAmount is summary metadata on the expediente, distinct from financing/simulation
 * amounts.
 */
public record Case(
    UUID id,
    UUID companyId,
    String reference,
    CaseStatus status,
    String operationType,
    BigDecimal requestedAmount,
    String description,
    UUID createdBy,
    Instant createdAt,
    Instant cancelledAt) {}
