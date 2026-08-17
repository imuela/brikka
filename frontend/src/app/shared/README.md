# shared

Reusable, presentation-only building blocks with no feature-specific business logic: UI
components, pipes, directives, form controls used across more than one feature.

- `directives/has-permission.directive.ts` (Sprint 13) — `*appHasPermission="'CODE'"` shows an
  element only if the session has that permission. UX-only, never a substitute for the backend's
  own authorization check (`03_TECHNICAL_SPECIFICATION.md` §3).

Otherwise still empty — populated as features that need shared UI are built, starting Sprint 14.
