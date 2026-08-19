# BRIKA — IMPLEMENTATION PLAN V1

> **ESTADO: SUPERSEDED (`ADR-PROCESS-001`).** Este documento queda como histórico y ya **no** es la fuente de verdad de ejecución. El plan de ejecución sprint a sprint autoritativo es `25_CLAUDE_CODE_EXECUTION_GUIDE.md`. Se conserva sin eliminar conforme a `CLAUDE.md` §3 (no eliminar versiones históricas sin política).

## Fase 0 — Foundation
- repository;
- Docker;
- PostgreSQL;
- Spring Boot;
- Angular;
- Flyway;
- CI.

## Fase 1 — Identity & Tenancy
- companies;
- users;
- roles;
- permissions;
- authentication;
- authorization;
- TenantContext.

## Fase 2 — CRM
- clients;
- client portal accounts;
- cases;
- participants;
- assignments;
- status history.

## Fase 3 — Property & Financing
- properties;
- simulations;
- financing;
- banks;
- bank requests;
- offers.

## Fase 4 — Documents
- document types;
- requirements;
- requests;
- documents;
- versions;
- storage;
- review.

## Fase 5 — Portal Cliente
- authentication;
- dashboard;
- operation visibility;
- document workflows;
- messaging;
- notifications.

## Fase 6 — Tasks & Communications
- tasks;
- conversations;
- messages;
- activities;
- notifications.

## Fase 7 — Scoring
- rules;
- calculation;
- history;
- explanation.

## Fase 8 — Reporting & Audit
- dashboards;
- reporting;
- exports;
- audit.

## Fase 9 — AI & Integrations
- AI Gateway;
- usage;
- integrations;
- events;
- webhooks.

## Fase 10 — Hardening
- security;
- performance;
- backups;
- observability;
- E2E;
- deployment;
- documentation.

## Regla

Claude Code deberá completar y validar cada fase antes de avanzar a la siguiente.
