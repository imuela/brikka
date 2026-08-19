/** Mirrors backend UserResponse (17_API_SPECIFICATION_DETAILED.md §5). companyId is null only for
 * SUPERADMIN (ADR-IDENTITY-001) — never observable through this screen in practice, since Users
 * endpoints always require a resolved tenant and SUPERADMIN never resolves one without a
 * SUPPORT_SESSION (not implemented). */
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
 * format or generate one automatically, so it must be supplied here. */
export interface CreateUserRequest {
  email: string;
  firstName: string;
  lastName: string;
  role: string;
  externalIdentityId: string;
}

/** Mirrors backend UpdateUserApiRequest — only these two fields are accepted by PATCH
 * /api/v1/users/{id}; email and role can never be changed after creation. */
export interface UpdateUserRequest {
  firstName: string;
  lastName: string;
}
