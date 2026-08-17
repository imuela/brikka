package com.brika.platform.document.web;

import com.brika.platform.document.DocumentType;
import java.util.UUID;

public record DocumentTypeResponse(UUID id, String code, String name, boolean active) {

  public static DocumentTypeResponse from(DocumentType documentType) {
    return new DocumentTypeResponse(
        documentType.id(), documentType.code(), documentType.name(), documentType.active());
  }
}
