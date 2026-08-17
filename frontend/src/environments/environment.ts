/**
 * Sprint 13: local development only (single environment). No production/staging
 * configuration exists yet — see ADR-FRONTEND-001, out of scope until Sprint 20.
 */
export const environment = {
  apiBaseUrl: 'http://localhost:8080',
  oidc: {
    issuer: 'http://localhost:18081/realms/brika',
    clientId: 'brika-frontend',
    redirectUri: 'http://localhost:4200/auth/callback',
    postLogoutRedirectUri: 'http://localhost:4200/login',
    scope: 'openid profile email',
  },
};
