/**
 * Auditoría UX/i18n pre-Sprint 16: única fuente de verdad para traducir al español los valores
 * de enum que el backend ya expone (nunca se modifican los valores internos que viajan a la API,
 * solo su representación visible). Un valor no presente en el mapa se muestra tal cual — evita
 * ocultar un estado real por una etiqueta desactualizada en vez de fallar en silencio.
 */

/** Mirrors backend CaseStatus (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §2). */
export const CASE_STATUS_LABELS: Record<string, string> = {
  PRESTUDY: 'Preestudio',
  DOCUMENTATION: 'Documentación',
  ANALYSIS: 'Análisis',
  BANK_SEARCH: 'Búsqueda de banco',
  BANK_SUBMISSION: 'Envío a banco',
  BANK_REVIEW: 'Revisión bancaria',
  OFFER: 'Oferta',
  FORMALIZATION: 'Formalización',
  COMPLETED: 'Completada',
  CANCELLED: 'Cancelada',
};

/** Mirrors backend CancellationReason (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §6). */
export const CANCELLATION_REASON_LABELS: Record<string, string> = {
  CLIENT_REQUEST: 'Solicitud del cliente',
  INELIGIBLE: 'No cumple los requisitos',
  NO_FINANCING: 'Sin financiación',
  PROPERTY_ISSUE: 'Problema con el inmueble',
  DUPLICATE: 'Duplicado',
  ABANDONED: 'Abandonada',
  OTHER: 'Otro',
};

/** Mirrors backend ParticipationType (chk_case_clients_participation_type). */
export const PARTICIPATION_TYPE_LABELS: Record<string, string> = {
  HOLDER: 'Titular',
  CO_HOLDER: 'Cotitular',
  GUARANTOR: 'Avalista',
  OTHER: 'Otro',
};

/** Mirrors backend ReviewStatus (documents.status and document_versions.review_status). */
export const REVIEW_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendiente',
  APPROVED: 'Aprobado',
  REJECTED: 'Rechazado',
};

/** Mirrors backend DocumentRequestStatus. */
export const DOCUMENT_REQUEST_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendiente',
  FULFILLED: 'Cumplida',
  CANCELLED: 'Cancelada',
};

/** clients.status has no documented enum — 'ACTIVE' is the only value the backend ever writes
 * (ClientRepository.insert, hardcoded default). */
export const CLIENT_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activo',
};

/** Mirrors backend UserRole (roles.code, ADR-RBAC-001). Internal values are kept as-is for API
 * calls and permission checks — only the visible label changes. */
export const ROLE_LABELS: Record<string, string> = {
  SUPERADMIN: 'Superadministrador',
  MANAGER: 'Manager',
  BROKER: 'Broker',
  CLIENT: 'Cliente',
};

/** Mirrors backend FinancingRequestStatus — server-controlled lifecycle marker, no documented
 * business catalog (see FinancingRequestStatus.java javadoc). */
export const FINANCING_REQUEST_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendiente',
  IN_PROGRESS: 'En curso',
  CLOSED: 'Cerrada',
};

/** Mirrors backend MatchResult (ADR-BANKENGINE-001 §4/§8). */
export const MATCH_RESULT_LABELS: Record<string, string> = {
  PASS: 'Cumple',
  FAIL: 'No cumple',
  WARNING: 'Advertencia',
  NOT_EVALUATED: 'Sin evaluar',
  ERROR: 'Error',
};

/** Mirrors backend bank_requests.status — server-controlled lifecycle marker, always 'SENT' in
 * this sprint (no endpoint changes it — see BankRequestRepository javadoc). */
export const BANK_REQUEST_STATUS_LABELS: Record<string, string> = {
  SENT: 'Enviada',
};

/** Mirrors backend bank_responses.status — always 'RECEIVED' (server-controlled). */
export const BANK_RESPONSE_STATUS_LABELS: Record<string, string> = {
  RECEIVED: 'Recibida',
};

/** Mirrors backend bank_offers.status — always 'RECEIVED' (server-controlled). */
export const BANK_OFFER_STATUS_LABELS: Record<string, string> = {
  RECEIVED: 'Recibida',
};

/** Mirrors backend final_financing.status — always 'ACTIVE' (server-controlled). */
export const FINAL_FINANCING_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activa',
};

/** Mirrors backend banks.status / bank_products.status — no documented business catalog beyond
 * 'ACTIVE' (BankRepository.insert hardcodes it), but BANK_UPDATE (SUPERADMIN) can write an
 * arbitrary value, so the pipe's raw-value fallback (see StatusLabelPipe) still applies. */
export const BANK_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activo',
};

/** Mirrors backend tasks.status (chk_tasks_status). DONE is only reachable via
 * POST /tasks/{id}/complete — never written by the update PATCH (TaskController javadoc). */
export const TASK_STATUS_LABELS: Record<string, string> = {
  TODO: 'Por hacer',
  IN_PROGRESS: 'En curso',
  BLOCKED: 'Bloqueada',
  DONE: 'Completada',
  CANCELLED: 'Cancelada',
};

/** Mirrors backend conversations.type (chk_conversations_type). SYSTEM is never produced by any
 * endpoint (ConversationController javadoc) so it is deliberately omitted here, not omitted by
 * oversight — the pipe's raw-value fallback would show it as-is if it were ever encountered. */
export const CONVERSATION_TYPE_LABELS: Record<string, string> = {
  CLIENT: 'Cliente',
  INTERNAL: 'Interna',
};

/** Mirrors backend conversations.status — always 'ACTIVE' (server-controlled, no endpoint changes
 * it — see ConversationRepository.insert). */
export const CONVERSATION_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activa',
};

/** Mirrors backend users.status — the only two values ever written (UserRepository.insert/
 * disable). No reactivation endpoint exists, so DISABLED is a terminal state in this UI. */
export const USER_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activo',
  DISABLED: 'Deshabilitado',
};

/** Mirrors backend companies.status — no CHECK constraint in the schema, but CompanyController
 * only ever writes these three values (insert/suspend/delete). Raw-value fallback still applies. */
export const COMPANY_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activa',
  SUSPENDED: 'Suspendida',
  DELETED: 'Eliminada',
};

/** Mirrors backend chk_company_subscriptions_status (V4 migration) — the closed set accepted by
 * PUT /api/v1/companies/{id}/subscription, plus CANCELLED which /cancel always writes. */
export const SUBSCRIPTION_STATUS_LABELS: Record<string, string> = {
  ACTIVE: 'Activa',
  TRIAL: 'Prueba',
  SUSPENDED: 'Suspendida',
  CANCELLED: 'Cancelada',
};

/** Sprint 20 (ADR-PROCESS-008): OPERATION_TYPES (features/cases/case.model.ts) — frontend-only
 * closed catalog, backend field remains free text (no CHECK constraint). */
export const OPERATION_TYPE_LABELS: Record<string, string> = {
  PURCHASE: 'Compra de vivienda',
  REFINANCE: 'Subrogación / cambio de banco',
  SELF_BUILD: 'Autopromoción',
  SECOND_MORTGAGE: 'Segunda hipoteca',
};

/** Sprint 20 (ADR-PROCESS-008): PROPERTY_TYPES (features/property/property.model.ts) —
 * frontend-only closed catalog, backend field remains free text (no CHECK constraint). */
export const PROPERTY_TYPE_LABELS: Record<string, string> = {
  FLAT: 'Piso',
  HOUSE: 'Casa',
  CHALET: 'Chalet',
  STUDIO: 'Estudio',
  COMMERCIAL_PREMISES: 'Local comercial',
  LAND: 'Suelo / parcela',
  GARAGE: 'Garaje',
};

/** Sprint 20 (ADR-PROCESS-008): ASSIGNMENT_TYPES (features/cases/case.model.ts) —
 * frontend-only closed catalog, backend field remains free text (no CHECK constraint). */
export const ASSIGNMENT_TYPE_LABELS: Record<string, string> = {
  PRIMARY: 'Responsable principal',
  SECONDARY: 'Colaborador',
  REVIEWER: 'Revisor',
};

/** Sprint 20 (ADR-PROCESS-008): TASK_TYPES (features/tasks/task.model.ts) — frontend-only
 * closed catalog, backend field remains free text (varchar(100), no CHECK constraint). */
export const TASK_TYPE_LABELS: Record<string, string> = {
  DOCUMENT_REVIEW: 'Revisión de documentación',
  CALL: 'Llamada',
  CLIENT_FOLLOWUP: 'Seguimiento al cliente',
  BANK_SUBMISSION: 'Envío a banco',
  INTERNAL: 'Tarea interna',
  GENERAL: 'General',
};
