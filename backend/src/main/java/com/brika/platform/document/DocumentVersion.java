package com.brika.platform.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersion(
    UUID id,
    UUID documentId,
    int versionNumber,
    String storageKey,
    String originalFilename,
    String mimeType,
    long sizeBytes,
    String checksum,
    UUID uploadedBy,
    Instant uploadedAt,
    ReviewStatus reviewStatus,
    UUID reviewedBy,
    Instant reviewedAt,
    String reviewComment) {}
