package com.brika.platform.portal.web;

import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentVersion;
import java.time.Instant;
import java.util.UUID;

public record PortalDocumentResponse(
    UUID id, UUID documentTypeId, int versionNumber, String originalFilename, Instant publishedAt) {

  public static PortalDocumentResponse from(
      Document document, DocumentVersion version, Instant publishedAt) {
    return new PortalDocumentResponse(
        document.id(),
        document.documentTypeId(),
        version.versionNumber(),
        version.originalFilename(),
        publishedAt);
  }
}
