package com.brika.platform.bankrequest;

import java.time.Instant;
import java.util.UUID;

/** ERD "FINAL_FINANCING": resultado seleccionado/final. case_id is UNIQUE — one per case. */
public record FinalFinancing(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID bankOfferId,
    String status,
    Instant finalizedAt,
    Instant createdAt,
    Instant updatedAt) {}
