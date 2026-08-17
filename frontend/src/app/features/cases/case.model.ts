/** Mirrors backend CaseStatus enum exactly (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §2). Only the
 * closed set of values is reused here — no transition graph is modeled in the frontend; the
 * backend rejects invalid transitions and the UI surfaces that error as-is. */
export const CASE_STATUSES = [
  'PRESTUDY',
  'DOCUMENTATION',
  'ANALYSIS',
  'BANK_SEARCH',
  'BANK_SUBMISSION',
  'BANK_REVIEW',
  'OFFER',
  'FORMALIZATION',
  'COMPLETED',
  'CANCELLED',
] as const;
export type CaseStatus = (typeof CASE_STATUSES)[number];

/** Mirrors backend CancellationReason enum exactly (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §6). */
export const CANCELLATION_REASONS = [
  'CLIENT_REQUEST',
  'INELIGIBLE',
  'NO_FINANCING',
  'PROPERTY_ISSUE',
  'DUPLICATE',
  'ABANDONED',
  'OTHER',
] as const;
export type CancellationReason = (typeof CANCELLATION_REASONS)[number];

/** Mirrors backend ParticipationType enum exactly (chk_case_clients_participation_type). */
export const PARTICIPATION_TYPES = ['HOLDER', 'CO_HOLDER', 'GUARANTOR', 'OTHER'] as const;
export type ParticipationType = (typeof PARTICIPATION_TYPES)[number];

/** Mirrors backend CaseResponse. */
export interface Case {
  id: string;
  companyId: string;
  reference: string;
  status: string;
  operationType: string;
  createdBy: string;
  createdAt: string;
  cancelledAt: string | null;
}

/** Mirrors backend CaseAssignmentResponse. */
export interface CaseAssignment {
  id: string;
  caseId: string;
  userId: string;
  assignmentType: string;
  active: boolean;
}

/** Mirrors backend CaseClientResponse. */
export interface CaseClient {
  clientId: string;
  firstName: string | null;
  lastName: string | null;
  participationType: string;
  isPrimary: boolean;
}

/** Mirrors backend CreateCaseApiRequest — operationType is free text, no catalog exists. */
export interface CreateCaseRequest {
  operationType: string;
}

/** Mirrors backend UpdateCaseApiRequest. */
export interface UpdateCaseRequest {
  operationType: string;
}

/** Mirrors backend ChangeCaseStatusApiRequest. `newStatus` is typed as `string` (not `CaseStatus`)
 * so it binds directly to a reactive form control populated from CASE_STATUSES — the backend is
 * still the one that validates and rejects an unknown value. */
export interface ChangeCaseStatusRequest {
  newStatus: string;
  reason: string;
}

/** Mirrors backend CancelCaseApiRequest. */
export interface CancelCaseRequest {
  reason: string;
  comment: string;
}

/** Mirrors backend ReopenCaseApiRequest. */
export interface ReopenCaseRequest {
  reason: string;
  targetStatus: string;
}

/** Mirrors backend CreateCaseAssignmentApiRequest — assignmentType is free text, no catalog
 * exists. */
export interface CreateCaseAssignmentRequest {
  userId: string;
  assignmentType: string;
}

/** Mirrors backend CaseClientApiRequest. `participationType` is `string` (see note on
 * ChangeCaseStatusRequest.newStatus above) — populated from PARTICIPATION_TYPES. */
export interface AddCaseClientRequest {
  clientId: string;
  participationType: string;
  isPrimary: boolean;
}

/** Minimal projection of backend UserResponse — only what the assignment picker needs. */
export interface AssignableUser {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
}
