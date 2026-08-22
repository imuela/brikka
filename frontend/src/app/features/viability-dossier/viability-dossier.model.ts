/** Sprint 32. Mirrors backend DocumentVersionResponse/GeneratedDocumentResponse exactly
 * (com.brika.platform.document.web). Shared shape with engagement-contract.model.ts. */
export interface GeneratedDocumentVersion {
  id: string;
  documentId: string;
  versionNumber: number;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  checksum: string;
  uploadedBy: string;
  uploadedAt: string;
  reviewStatus: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
}

export interface GeneratedDocument {
  documentId: string | null;
  versions: GeneratedDocumentVersion[];
}
