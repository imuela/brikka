package com.brika.platform.document.web;

import com.brika.platform.document.DocumentVersion;
import java.time.Instant;
import java.util.UUID;

public record DocumentVersionResponse(
    UUID id,
    UUID documentId,
    int versionNumber,
    String originalFilename,
    String mimeType,
    long sizeBytes,
    String checksum,
    UUID uploadedBy,
    Instant uploadedAt,
    String reviewStatus,
    UUID reviewedBy,
    Instant reviewedAt,
    String reviewComment) {

  public static DocumentVersionResponse from(DocumentVersion version) {
    return new DocumentVersionResponse(
        version.id(),
        version.documentId(),
        version.versionNumber(),
        version.originalFilename(),
        version.mimeType(),
        version.sizeBytes(),
        version.checksum(),
        version.uploadedBy(),
        version.uploadedAt(),
        version.reviewStatus().name(),
        version.reviewedBy(),
        version.reviewedAt(),
        version.reviewComment());
  }
}
