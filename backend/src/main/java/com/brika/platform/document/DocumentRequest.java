package com.brika.platform.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentRequest(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID documentTypeId,
    UUID requestedFromClientId,
    DocumentRequestStatus status,
    Instant dueAt,
    UUID requestedBy,
    UUID requirementId) {}
