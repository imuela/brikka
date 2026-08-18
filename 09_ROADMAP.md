# BRIKA — ROADMAP V1

Este documento es el mapa **macro** de fases para negocio/stakeholders. El plan de ejecución sprint a sprint autoritativo para Claude Code es `25_CLAUDE_CODE_EXECUTION_GUIDE.md` (`ADR-PROCESS-001`).

## Correspondencia Fase ↔ Sprint

| Fase | Sprint(s) en `25_CLAUDE_CODE_EXECUTION_GUIDE.md` |
|---|---|
| A — Consolidación documental | — (previa a Sprint 0) |
| B — Diseño técnico detallado | — (previa a Sprint 0) |
| C — Foundation | Sprint 0, Sprint 1 |
| D — Identity/Tenancy | Sprint 2 |
| E — CRM/Cases | Sprint 3 |
| F — Documents | Sprint 4 |
| G — Banking/Financing | Sprint 5, Sprint 6 |
| H — Portal Cliente | Sprint 7 |
| I — Scoring/Workflow | Sprint 8 (workflow ya en Sprint 3), Sprint 9 |
| J — Audit/Reporting/AI | Sprint 10, Sprint 11 |
| K — Hardening | Sprint 12 |
| L — Frontend V1 | Sprint 13, Sprint 14, Sprint 15, Sprint 16, Sprint 17 (`ADR-PROCESS-004`, `ADR-PROCESS-005`) |

## Fase A — Consolidación documental
- especificación;
- decisiones;
- trazabilidad;
- revisión de contradicciones.

## Fase B — Diseño técnico detallado
- ERD conceptual definitivo (completado en `15_DEFINITIVE_ERD.md`).
- arquitectura definitiva;
- contratos API;
- modelo conceptual;
- permisos;
- workflows.

## Fase C — Foundation
- repositorio;
- CI;
- Docker;
- backend;
- frontend;
- PostgreSQL;
- Flyway.

## Fase D — Identity/Tenancy
- autenticación;
- usuarios;
- roles;
- permisos;
- planes/entitlements/suscripciones de empresa (`ADR-PLATFORM-001`);
- aislamiento.

## Fase E — CRM/Cases
- clientes;
- operaciones;
- participantes;
- inmueble;
- estados.

## Fase F — Documents
- catálogo de requisitos (`document_requirements`, condicionado por tipo de operación/perfil/banco/producto — `ADR-DOC-001`);
- solicitudes;
- versiones (incluye metadatos de fichero, sin tabla `files` independiente);
- storage;
- revisión.

## Fase G — Banking/Financing
- simulaciones;
- bancos;
- criterios;
- solicitudes;
- ofertas.

## Fase H — Portal Cliente
- acceso;
- dashboard;
- documentación;
- mensajería (con `conversation_participants` obligatorio y adjuntos — `ADR-COMMS-001`, `ADR-COMMS-002`);
- notificaciones (`IN_APP`/`EMAIL` en V1 — `ADR-NOTIF-001`);
- publicación.

## Fase I — Scoring/Workflow
- scoring;
- reglas;
- workflows;
- automatizaciones;
- actividad funcional de negocio (`activities`, distinta de auditoría — `ADR-AUDIT-001`).

## Fase J — Audit/Reporting/AI
- auditoría (`audit_events`);
- reporting;
- AI Gateway + Python Worker aislado de PostgreSQL (`ADR-AI-001`);
- casos IA autorizados;
- integraciones mínimas (`ADR-INTEGRATIONS-001`, sin proveedores concretos).

## Fase K — Hardening
- seguridad;
- rendimiento;
- observabilidad;
- backups;
- pruebas E2E;
- despliegue.

## Fase L — Frontend V1
- CRM/Operaciones (clientes, casos);
- Inmueble/Documentación;
- Financing/Simulations/Bank Matching/Ofertas;
- Tasks/Comunicaciones internas/Notificaciones (Sprint 17, `ADR-PROCESS-005`) — Portal Cliente (Sprint 19) y administración (Users/Companies/Plans, Sprint 18 candidato) quedan como los dos bloques independientes aún pendientes tras esta fase (`ADR-PROCESS-004`).

## Regla de progreso

No se considera completada una fase hasta cumplir sus criterios de aceptación y pruebas.
