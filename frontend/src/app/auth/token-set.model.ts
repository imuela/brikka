/** In-memory session state — never persisted (ADR-FRONTEND-001, "no guardar secretos en
 * localStorage"). Shared shape between AuthService and PortalAuthService: a plain data carrier,
 * not identity-resolution logic, so sharing it does not blur ADR-PORTAL-AUTH-001's boundary. */
export interface TokenSet {
  accessToken: string;
  refreshToken: string;
  /** Epoch milliseconds. */
  expiresAt: number;
}

/** Mirrors the backend's AccessTokenApiResponse (Sprint 22 authorization, self-issued auth). */
export interface AccessTokenApiResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}
