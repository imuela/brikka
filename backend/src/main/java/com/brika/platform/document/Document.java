package com.brika.platform.document;

import java.util.UUID;

public record Document(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID documentTypeId,
    UUID currentVersionId,
    ReviewStatus status) {}
