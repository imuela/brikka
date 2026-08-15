# core

Singleton, app-wide concerns: HTTP interceptors, API client base configuration, app-level
guards, global state services, error handling. Imported once, never by feature modules
directly for UI.

Empty in Sprint 1 (foundation only, per `25_CLAUDE_CODE_EXECUTION_GUIDE.md`). Identity/session
state and the auth interceptor arrive in Sprint 2 alongside `auth/`.
