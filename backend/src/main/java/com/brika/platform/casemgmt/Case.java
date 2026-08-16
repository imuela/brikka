package com.brika.platform.casemgmt;

import java.time.Instant;
import java.util.UUID;

public record Case(
    UUID id,
    UUID companyId,
    String reference,
    CaseStatus status,
    String operationType,
    UUID createdBy,
    Instant createdAt,
    Instant cancelledAt) {}
