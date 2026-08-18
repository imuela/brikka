/** Mirrors backend PortalDocumentResponse — only published documents are ever returned
 * (PortalDocumentController.publishedViewOf). */
export interface PortalDocument {
  id: string;
  documentTypeId: string;
  versionNumber: number;
  originalFilename: string;
  publishedAt: string;
}

/** Mirrors backend PortalDocumentRequestResponse (Sprint 19, ADR-PROCESS-007) — carries the
 * resolved document type name/code since the Portal token cannot call GET /api/v1/document-types
 * (outside the /api/v1/portal/** security matcher). */
export interface PortalDocumentRequest {
  id: string;
  documentTypeId: string;
  documentTypeCode: string;
  documentTypeName: string;
  status: string;
  dueAt: string | null;
}
