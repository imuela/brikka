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

/** Mirrors backend CreateUserApiRequest. externalIdentityId must already exist in the identity
 * provider — Keycloak account provisioning/sync is not implemented, so it cannot be generated or
 * validated from the frontend. */
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
