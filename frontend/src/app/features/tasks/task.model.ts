/** Sprint 20 (ADR-PROCESS-008): type is free text in the backend contract (varchar(100), no CHECK
 * constraint — see Task.java). Frontend-only closed catalog approved explicitly by the project
 * owner; the backend still accepts any string. DOCUMENT_REVIEW and CALL are the values in real use
 * before this sprint and remain valid unchanged. */
export const TASK_TYPES = [
  'DOCUMENT_REVIEW',
  'CALL',
  'CLIENT_FOLLOWUP',
  'BANK_SUBMISSION',
  'INTERNAL',
  'GENERAL',
] as const;
export type TaskType = (typeof TASK_TYPES)[number];

/** Mirrors backend com.brika.platform.task.web.TaskResponse (17_API_SPECIFICATION_DETAILED.md §17).
 * caseId/assignedTo are nullable — a task may exist independent of any case, and be unassigned. */
export interface Task {
  id: string;
  caseId: string | null;
  assignedTo: string | null;
  type: string;
  title: string;
  description: string | null;
  status: string;
  dueAt: string | null;
  createdBy: string;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors CreateTaskApiRequest. `type` is `string` (see note on
 * ChangeCaseStatusRequest.newStatus in case.model.ts) — populated from TASK_TYPES. */
export interface CreateTaskRequest {
  caseId: string | null;
  assignedTo: string | null;
  type: string;
  title: string;
  description: string | null;
  dueAt: string | null;
}

/** Mirrors UpdateTaskApiRequest — full-replace PATCH. status excludes DONE (backend rejects it;
 * completing a task always goes through POST /tasks/{id}/complete). */
export interface UpdateTaskRequest {
  title: string;
  description: string | null;
  status: string;
  dueAt: string | null;
  assignedTo: string | null;
}
