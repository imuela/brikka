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

/** Sprint 20 (ADR-PROCESS-008): operationType is free text in the backend contract (no CHECK
 * constraint, no enum — see CreateCaseApiRequest.java) and was previously a plain text input for
 * exactly that reason. This is a frontend-only closed catalog approved explicitly by the project
 * owner during Sprint 20 (Brikka is a mortgage-only product), not derived from any backend enum —
 * the backend still accepts any string. Legacy data seeded as "MORTGAGE" before this sprint was
 * migrated to PURCHASE (V16__normalize_operation_type_seed_data.sql). */
export const OPERATION_TYPES = ['PURCHASE', 'REFINANCE', 'SELF_BUILD', 'SECOND_MORTGAGE'] as const;
export type OperationType = (typeof OPERATION_TYPES)[number];

/** Sprint 20 (ADR-PROCESS-008): assignmentType is free text in the backend contract (no CHECK
 * constraint, no enum — see CreateCaseAssignmentApiRequest.java). Frontend-only closed catalog
 * approved explicitly by the project owner during Sprint 20; the backend still accepts any
 * string. PRIMARY is the only value in real use before this sprint and remains valid unchanged. */
export const ASSIGNMENT_TYPES = ['PRIMARY', 'SECONDARY', 'REVIEWER'] as const;
export type AssignmentType = (typeof ASSIGNMENT_TYPES)[number];

/** Mirrors backend CaseResponse (Sprint 27, Bloque 4 adds requestedAmount/description). */
export interface Case {
  id: string;
  companyId: string;
  reference: string;
  status: string;
  operationType: string;
  requestedAmount: number | null;
  description: string | null;
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

/** Mirrors backend CreateCaseApiRequest. `operationType` is `string` (see note on
 * ChangeCaseStatusRequest.newStatus above) — populated from OPERATION_TYPES. Sprint 27, Bloque 4
 * adds optional requestedAmount/description. */
export interface CreateCaseRequest {
  operationType: string;
  requestedAmount?: number | null;
  description?: string | null;
}

/** Mirrors backend UpdateCaseApiRequest (Sprint 27, Bloque 4). */
export interface UpdateCaseRequest {
  operationType: string;
  requestedAmount?: number | null;
  description?: string | null;
}

/** Mirrors backend ChangeCaseStatusApiRequest. `newStatus` is typed as `string` (not `CaseStatus`)
 * so it binds directly to a reactive form control populated from CASE_STATUSES — the backend is
 * still the one that validates and rejects an unknown value. */
export interface ChangeCaseStatusRequest {
  newStatus: string;
  reason: string;
  /** BRIKKA V2 I3: force the transition past its business precondition. Needs the
   * CASE_TRANSITION_OVERRIDE permission and a non-blank reason (both enforced by the backend). */
  override?: boolean;
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

/** Mirrors backend CreateCaseAssignmentApiRequest. `assignmentType` is `string` (see note on
 * ChangeCaseStatusRequest.newStatus above) — populated from ASSIGNMENT_TYPES. */
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
