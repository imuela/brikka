package com.brika.platform.document.web;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 32. Shared response shape for a case's generated document (contrato/dossier): the Document
 * id plus every DocumentVersion generated so far (its history), newest first. Both fields are
 * {@code null}/empty when nothing has been generated yet.
 */
public record GeneratedDocumentResponse(UUID documentId, List<DocumentVersionResponse> versions) {}
