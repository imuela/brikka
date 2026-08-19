# auth

Email/password login against Brika's own backend (Sprint 22, ADR-AUTH-001), session handling,
route guards tied to authentication state. Internal users only (SUPERADMIN/MANAGER/BROKER) — the
Portal Cliente equivalent lives in `features/portal/auth/` (separate stack, `ADR-PORTAL-AUTH-001`).

- `auth.service.ts` — calls `/api/v1/auth/login`, `/refresh`, `/logout` on Brika's own backend.
  Tokens live in memory only (never localStorage/sessionStorage); no PKCE/redirect flow — this
  replaced the Keycloak Authorization Code + PKCE flow used until Sprint 22.
- `login/` — the `/login` page (email + password form).
- `password-reset/` — request/confirm password-reset screens.
- `auth.guard.ts` / `permission.guard.ts` — UX-only route guards (never a security boundary; the
  backend is always the authority, `03_TECHNICAL_SPECIFICATION.md` §3).
- `token-set.model.ts` — in-memory access/refresh token pair shape.
