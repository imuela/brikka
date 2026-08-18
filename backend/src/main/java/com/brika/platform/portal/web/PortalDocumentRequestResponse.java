package com.brika.platform.portal.web;

import com.brika.platform.document.DocumentRequest;
import com.brika.platform.document.DocumentType;
import java.time.Instant;
import java.util.UUID;

/**
 * Sprint 19 (ADR-PROCESS-007): deliberately narrower than the internal DocumentRequestResponse —
 * omits companyId/requestedBy/requirementId (internal-only fields, same convention already followed
 * by PortalCaseResponse/PortalDocumentResponse). Carries the resolved document type name/code
 * alongside documentTypeId: the Portal token cannot call GET /api/v1/document-types (that endpoint
 * lives outside the /api/v1/portal/** security matcher, validated only against the internal realm),
 * so the name must be embedded here or the client would see a bare UUID — documentTypeId itself is
 * still included because the frontend's "Subir documento" action for a given request needs it to
 * call POST /portal/cases/{id}/documents (documentTypeId is a foreign key into a global,
 * non-tenant-owned catalog, so exposing it here carries no isolation risk).
 */
public record PortalDocumentRequestResponse(
    UUID id,
    UUID documentTypeId,
    String documentTypeCode,
    String documentTypeName,
    String status,
    Instant dueAt) {

  public static PortalDocumentRequestResponse from(DocumentRequest request, DocumentType type) {
    return new PortalDocumentRequestResponse(
        request.id(),
        type.id(),
        type.code(),
        type.name(),
        request.status().name(),
        request.dueAt());
  }
}
