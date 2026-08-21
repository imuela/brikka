/** Mirrors backend UserResponse (17_API_SPECIFICATION_DETAILED.md §5). companyId is null only for
 * SUPERADMIN (ADR-IDENTITY-001) — a GLOBAL SUPERADMIN reads every company's users (Sprint 27,
 * ADR-RBAC-002; no SUPPORT_SESSION needed for this). */
export interface User {
  id: string;
  companyId: string | null;
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  status: string;
}

/** Mirrors backend CreateUserApiRequest. externalIdentityId is an opaque, unique login identifier
 * for this user (Sprint 22, ADR-AUTH-001 — Brika's own auth); the backend does not validate its
 * format or generate one automatically, so it must be supplied here. companyId (Sprint 27,
 * ADR-RBAC-002) is required when the caller is a GLOBAL SUPERADMIN (who has no tenant of their
 * own) and ignored otherwise — the backend always resolves MANAGER/BROKER's own tenant server-side. */
export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  externalIdentityId: string;
  companyId?: string;
}

/** Mirrors backend UpdateUserApiRequest — only these two fields are accepted by PATCH
 * /api/v1/users/{id}; email and role can never be changed after creation. */
export interface UpdateUserRequest {
  firstName: string;
  lastName: string;
}
