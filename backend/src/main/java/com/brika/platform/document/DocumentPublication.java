package com.brika.platform.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentPublication(
    UUID id,
    UUID companyId,
    UUID documentId,
    UUID documentVersionId,
    boolean publishedToPortal,
    UUID publishedBy,
    Instant publishedAt,
    Instant revokedAt) {}
