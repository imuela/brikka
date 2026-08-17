# features

One subfolder per business domain (e.g. `clients/`, `cases/`, `documents/`, `portal/`), each
package-by-feature: its own components, services, routes, state.

`shell/` (Sprint 13, ADR-FRONTEND-001) is the one exception — it is foundation, not a business
domain: the authenticated app layout (header, sidenav, user menu) plus a placeholder dashboard
that proves the auth/session pipeline end to end.

`clients/` and `cases/` (Sprint 14) are the first real business domains: CRM (Clientes) and
Operations (Casos), covering listing, detail, create/edit, status changes, cancellation,
reopening, assignment, and case-client management — each a thin layer over the existing Sprint 3
backend endpoints, gated by `permissionGuard`/`*appHasPermission` per the real RBAC permission
catalog. Property, Documents, Financing and later domains are out of scope until their own
sprints.
