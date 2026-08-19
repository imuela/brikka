/**
 * Sprint 13: local development only (single environment). No production/staging
 * configuration exists yet — see ADR-FRONTEND-001, out of scope until Sprint 20.
 *
 * Sprint 22 cierre (autenticación propia — ADR-AUTH-001, 27_KEYCLOAK_REMOVAL_ANALYSIS.md):
 * login/refresh/logout/password-reset target Brika's own backend under apiBaseUrl
 * (/api/v1/auth/*, /api/v1/portal/auth/*) — no separate issuer/client config needed, unlike the
 * Keycloak redirect flow this replaced. Keycloak has since been fully retired from the local
 * environment: Brika's own tokens are the only ones the backend accepts, unconditionally.
 */
export const environment = {
  apiBaseUrl: 'http://localhost:8080',
};
