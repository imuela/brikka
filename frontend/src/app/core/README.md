# core

Singleton, app-wide concerns: HTTP interceptors, API client base configuration, app-level
guards, global state services, error handling. Imported once, never by feature modules
directly for UI.

Implemented in Sprint 13 (ADR-FRONTEND-001):

- `http/api-client.ts` — thin `HttpClient` wrapper prefixing `environment.apiBaseUrl`.
- `http/auth.interceptor.ts` — attaches `Authorization: Bearer <token>` only to requests
  targeting our own API (never to the backend's own unauthenticated auth endpoints, SKIP_AUTH).
- `http/error.interceptor.ts` — normalizes backend errors into `ApiError`; a 401 clears the
  session and redirects to `/login` without assuming a `{code,message,requestId}` body (Spring
  Security answers 401s before `GlobalExceptionHandler` ever runs).
- `http/http-context.ts` — `SKIP_AUTH` token marking non-Brika-API requests.
- `session/session.store.ts` — signal-based read model (`user`, `role`, `companyId`,
  `hasPermission(code)`).
- `session/session.service.ts` — hydrates `SessionStore` from `GET /me` + `GET /me/permissions`
  right after login.
