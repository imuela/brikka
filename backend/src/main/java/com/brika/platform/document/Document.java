package com.brika.platform.document;

import java.util.UUID;

/**
 * clientId (V27, BRIKKA V2 I1) attributes a document to a case holder for the per-holder document
 * checklist. NULL means "document of the expediente" — every pre-V2 document and every current
 * upload/generation flow that does not pass a client keeps client_id NULL, unchanged.
 */
public record Document(
    UUID id,
    UUID companyId,
    UUID caseId,
    UUID documentTypeId,
    UUID clientId,
    UUID currentVersionId,
    ReviewStatus status) {}
