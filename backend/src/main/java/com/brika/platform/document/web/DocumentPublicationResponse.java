package com.brika.platform.document.web;

import com.brika.platform.document.DocumentPublication;
import java.time.Instant;
import java.util.UUID;

public record DocumentPublicationResponse(
    UUID id,
    UUID documentId,
    UUID documentVersionId,
    boolean publishedToPortal,
    Instant publishedAt) {

  public static DocumentPublicationResponse from(DocumentPublication publication) {
    return new DocumentPublicationResponse(
        publication.id(),
        publication.documentId(),
        publication.documentVersionId(),
        publication.publishedToPortal(),
        publication.publishedAt());
  }
}
