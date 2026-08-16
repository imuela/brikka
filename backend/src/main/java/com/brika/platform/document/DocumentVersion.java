package com.brika.platform.document;

import java.time.Instant;
import java.util.UUID;

/**
 * Exactly one of uploadedBy / uploadedByClientId is set (chk_document_versions_single_uploader).
 */
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
    UUID uploadedByClientId,
    Instant uploadedAt,
    ReviewStatus reviewStatus,
    UUID reviewedBy,
    Instant reviewedAt,
    String reviewComment) {}
