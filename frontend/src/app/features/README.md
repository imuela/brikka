# features

One subfolder per business domain (e.g. `clients/`, `cases/`, `documents/`, `portal/`), each
package-by-feature: its own components, services, routes, state.

`shell/` (Sprint 13, ADR-FRONTEND-001) is the one exception — it is foundation, not a business
domain: the authenticated app layout (header, sidenav, user menu) plus a placeholder dashboard
that proves the auth/session pipeline end to end. No other feature exists yet; Sprint 14 adds
the first real business domain.
