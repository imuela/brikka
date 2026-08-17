# auth

OAuth/OIDC login flow (Authorization Code + PKCE, per `19_IDENTITY_OAUTH_SPECIFICATION.md`),
session handling, route guards tied to authentication state.

Implemented in Sprint 13 (ADR-FRONTEND-001) for internal users only:

- `pkce.ts` — PKCE `code_verifier`/`code_challenge` (S256), hand-rolled on the Web Crypto API.
- `auth.service.ts` — Authorization Code + PKCE against Keycloak's `brika` realm. Tokens live
  in memory only (never localStorage/sessionStorage); only the one-time PKCE `code_verifier`/
  `state` pair uses sessionStorage across the redirect.
- `login/` — the `/login` page.
- `callback/` — `/auth/callback`, completes the code-for-token exchange and hydrates the session.
- `auth.guard.ts` / `permission.guard.ts` — UX-only route guards (never a security boundary; the
  backend is always the authority, `03_TECHNICAL_SPECIFICATION.md` §3).

The Portal Cliente (separate Keycloak realm, `ADR-PORTAL-AUTH-001`) is not implemented here —
out of Sprint 13 scope, planned for Sprint 19.
