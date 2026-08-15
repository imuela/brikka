# auth

OAuth/OIDC login flow (Authorization Code + PKCE, per `19_IDENTITY_OAUTH_SPECIFICATION.md`),
session handling, route guards tied to authentication state.

Empty in Sprint 1. Implemented in Sprint 2 (Identity + Tenant + RBAC), separately for internal
users and for the Portal Cliente per `ADR-PLATFORM-001`/`06_SECURITY_SPECIFICATION.md`.
