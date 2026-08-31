package com.brika.platform.document.web;

import com.brika.platform.document.Document;
import java.util.UUID;

public record DocumentResponse(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID documentTypeId,
    UUID clientId,
    UUID currentVersionId,
    String status) {

  public static DocumentResponse from(Document document) {
    return new DocumentResponse(
        document.id(),
        document.companyId(),
        document.caseId(),
        document.documentTypeId(),
        document.clientId(),
        document.currentVersionId(),
        document.status().name());
  }
}
