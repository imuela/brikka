package com.brika.platform.document.web;

import com.brika.platform.document.DocumentRequest;
import java.time.Instant;
import java.util.UUID;

public record DocumentRequestResponse(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID documentTypeId,
    UUID requestedFromClientId,
    String status,
    Instant dueAt,
    UUID requestedBy,
    UUID requirementId) {

  public static DocumentRequestResponse from(DocumentRequest request) {
    return new DocumentRequestResponse(
        request.id(),
        request.companyId(),
        request.caseId(),
        request.documentTypeId(),
        request.requestedFromClientId(),
        request.status().name(),
        request.dueAt(),
        request.requestedBy(),
        request.requirementId());
  }
}
