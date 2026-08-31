/** Mirrors backend DocumentType (document_types global catalog, V2__seed_system_catalogs.sql). */
export interface DocumentType {
  id: string;
  code: string;
  name: string;
  active: boolean;
}

/** Mirrors backend DocumentResponse. Named CaseDocument (not Document) to avoid shadowing the DOM
 * global. status is the document's own review status (ReviewStatus), separate from any per-version
 * reviewStatus. */
export interface CaseDocument {
  id: string;
  companyId: string;
  caseId: string;
  documentTypeId: string;
  currentVersionId: string | null;
  status: string;
}

/** Mirrors backend DocumentVersionResponse. */
export interface CaseDocumentVersion {
  id: string;
  documentId: string;
  versionNumber: number;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  checksum: string;
  uploadedBy: string | null;
  uploadedAt: string;
  reviewStatus: string;
  reviewedBy: string | null;
  reviewedAt: string | null;
  reviewComment: string | null;
}

/** Mirrors backend CreateDocumentApiRequest. clientId is optional (BRIKKA V2 I1): when set, the
 * document is attributed to that case holder so it can satisfy a per-holder checklist requirement. */
export interface CreateCaseDocumentRequest {
  documentTypeId: string;
  clientId?: string | null;
}

/** Mirrors backend CaseChecklistResponse.Item (BRIKKA V2 I1). state is one of MISSING / SUBMITTED /
 * REJECTED / APPROVED; only APPROVED counts as satisfied. clientId is null for a document of the
 * expediente, or the holder the requirement is addressed to. */
export interface CaseChecklistItem {
  requirementId: string;
  documentRequestId: string | null;
  documentTypeId: string;
  documentTypeCode: string | null;
  documentTypeName: string | null;
  mandatory: boolean;
  clientId: string | null;
  state: string;
}

/** Mirrors backend CaseChecklistResponse (BRIKKA V2 I1). complete = every mandatory item APPROVED. */
export interface CaseChecklist {
  mandatoryTotal: number;
  mandatoryMissing: number;
  optionalTotal: number;
  optionalMissing: number;
  complete: boolean;
  items: CaseChecklistItem[];
}

/** Mirrors backend ReviewDocumentApiRequest. decision must be APPROVED or REJECTED — "request a
 * new version" is REJECTED with an explanatory comment, not a third decision value. */
export interface ReviewCaseDocumentRequest {
  decision: string;
  comment: string;
}

/** Mirrors backend DocumentPublicationResponse. */
export interface CaseDocumentPublication {
  id: string;
  documentId: string;
  documentVersionId: string;
  publishedToPortal: boolean;
  publishedAt: string;
}

/** Mirrors backend DownloadUrlResponse — always a short-lived presigned Object Storage URL, never
 * the raw storage key or credentials. */
export interface DownloadUrl {
  url: string;
  expiresInSeconds: number;
}

/** Mirrors backend DocumentRequestResponse. */
export interface CaseDocumentRequest {
  id: string;
  companyId: string;
  caseId: string;
  documentTypeId: string;
  requestedFromClientId: string | null;
  status: string;
  dueAt: string | null;
  requestedBy: string;
  requirementId: string | null;
}

/** Mirrors backend CreateDocumentRequestApiRequest. requirementId is always null here —
 * document-requirements management/linking is explicitly out of Sprint 15 scope. */
export interface CreateCaseDocumentRequestRequest {
  documentTypeId: string;
  requestedFromClientId: string | null;
  dueAt: string | null;
  requirementId: string | null;
}

/** Mirrors backend UpdateDocumentRequestApiRequest. status must be one of: PENDING, FULFILLED,
 * CANCELLED. */
export interface UpdateCaseDocumentRequestRequest {
  status: string;
}
