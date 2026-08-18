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

/** Mirrors CreateTaskApiRequest. type is free text (varchar(100), no CHECK constraint in the
 * schema — see Task.java), so it stays a plain text field rather than an invented catalog. */
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
