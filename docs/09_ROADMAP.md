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
| L — Frontend V1 | Sprint 13, Sprint 14, Sprint 15, Sprint 16, Sprint 17, Sprint 18, Sprint 19 (`ADR-PROCESS-004`, `ADR-PROCESS-005`, `ADR-PROCESS-006`, `ADR-PROCESS-007`) |
| M — Rebranding Brikka + normalización UX | Sprint 20 (`ADR-PROCESS-008`) — última fase antes de la auditoría general (Sprint 21 en adelante) |
| N — Autenticación propia (sustitución de Keycloak) | Sprint 22 (`ADR-AUTH-001` y adenda de cierre) — completado: Keycloak retirado, emisor JWT propio, Argon2id, recuperación de contraseña con Mailpit local, 8 usuarios de desarrollo verificados |
| O — Auditoría general y consolidación funcional (reconstrucción histórica — ver nota) | Sprint 21 (sin evidencia de ejecución como unidad propia — hueco documental, ver `12_DECISION_LOG.md` y el propio git log), Sprint 23 (adenda: hidratación de sesión tras recarga, `12_DECISION_LOG.md`), Sprint 24 (`ADR-ENV-001`: entornos LOCAL/TEST/PROD, claves JWT persistentes, seed reproducible), Sprint 25 (`ADR-NOTIF-002`: eventos de dominio conectados a notificaciones IN_APP), Sprint 26 (`ADR-NOTIF-003`: transporte asíncrono de notificaciones por RabbitMQ), Sprint 27 (`ADR-RBAC-002`, `29_SPRINT_27_IMPLEMENTATION_REPORT.md`: SUPERADMIN global, Dashboard, Cliente/Caso ampliados), bloque de hardening de dependencias post-Sprint-27 (Spring Boot 3.5.16, 0 CRITICAL/0 HIGH Java en Trivy, commit `78bead5`), Sprint 28 (`ADR-RBAC-002` consolidación: modelo SUPERADMIN/RBAC reconciliado con el código real, gap de `CLIENT_PORTAL_ACCOUNT_CREATE` confirmado correcto por diseño, alcance real de SUPPORT_SESSION acotado) |

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
- acceso (backend Sprint 7, frontend Sprint 19 — `ADR-PORTAL-AUTH-001`, `ADR-PROCESS-007`);
- dashboard;
- documentación (incluye vista explícita de solicitudes pendientes, Sprint 19);
- mensajería (con `conversation_participants` obligatorio y adjuntos — `ADR-COMMS-001`, `ADR-COMMS-002`);
- notificaciones (`IN_APP`/`EMAIL` en V1 — `ADR-NOTIF-001`; lectura y marcado de leída en Sprint 19, sin productor conectado);
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
- Tasks/Comunicaciones internas/Notificaciones (Sprint 17, `ADR-PROCESS-005`);
- Administración: Users/Companies/Plans/Subscriptions (Sprint 18, `ADR-PROCESS-006`);
- Portal Cliente (Sprint 19, `ADR-PROCESS-007`, `ADR-PORTAL-AUTH-001`) — sin ningún bloque de Fase L pendiente tras este sprint.

## Fase M — Rebranding Brikka + normalización UX
- marca visible del producto: Brika → **Brikka** (identificadores técnicos internos preservados);
- imagen de marca analizada y documentada (`docs/branding/BRIKKA_BRAND_GUIDELINES.md`, `BRIKKA_BRAND_REVIEW.md`), **pendiente de aprobación humana** — no considerada una identidad de marca definitiva por el mero hecho de estar documentada;
- auditoría y normalización de textos visibles en español en toda la aplicación (interno + Portal Cliente);
- 4 campos "Tipo" (operación, inmueble, asignación, tarea) convertidos de texto libre a catálogo cerrado aprobado explícitamente por el responsable del proyecto (Sprint 20, `ADR-PROCESS-008`) — ninguno tenía un catálogo real preexistente en backend ni frontend.

**A partir de aquí continúa la auditoría general del proyecto (Sprint 21 en adelante).** Sprint 22
(autenticación propia, Keycloak retirado) ha cerrado la Fase N y deja la aplicación funcional de
extremo a extremo en local sin dependencias de identidad externas.

## Fase O — Auditoría general y consolidación funcional

**Nota de reconstrucción histórica (Sprint 28):** esta fase, a diferencia de las anteriores, no fue
planificada por adelantado en este documento ni en `25_CLAUDE_CODE_EXECUTION_GUIDE.md` — ambos
quedaron detenidos en el Sprint 20/22 respectivamente mientras el trabajo real de Sprints 21-27
avanzaba en el repositorio. Lo que sigue se ha reconstruido a partir de `git log`, los tags
existentes y las ADR correspondientes en `12_DECISION_LOG.md`, no de una definición original de
sprint — se marca explícitamente para no aparentar una planificación que no existió en su momento.

- **Sprint 21**: sin evidencia de ejecución como unidad de trabajo propia (cero commits entre
  `af19ed1` (Sprint 20) y `69901f2` (Sprint 22), cero documento que lo mencione por nombre).
- **Sprint 22**: autenticación propia, retirada de Keycloak (Fase N, ver arriba).
- **Sprint 23**: corrección de un P0 heredado — hidratación de sesión tras recarga (F5), persistencia
  del refresh token en `sessionStorage` (adenda documentada en `12_DECISION_LOG.md`, sin informe de
  cierre propio).
- **Sprint 24**: separación de entornos LOCAL/TEST/PROD, claves JWT persistentes, email SMTP real,
  seed reproducible (`ADR-ENV-001`, sin informe de cierre propio).
- **Sprint 25**: notificaciones IN_APP conectadas a eventos de dominio reales (`ADR-NOTIF-002`, sin
  informe de cierre propio).
- **Sprint 26**: transporte asíncrono de notificaciones vía RabbitMQ (`ADR-NOTIF-003`, sin informe de
  cierre propio).
- **Sprint 27**: SUPERADMIN administrador global, Dashboard básico, Cliente y Caso ampliados
  (`ADR-RBAC-002`, `29_SPRINT_27_IMPLEMENTATION_REPORT.md`).
- **Bloque de hardening de dependencias** (post-Sprint-27, previo a Sprint 28): Spring Boot 3.3.4 →
  3.5.16, BouncyCastle, RabbitMQ client, Netty y PostgreSQL driver actualizados — 55 → 0
  vulnerabilidades Java CRITICAL/HIGH confirmadas por Trivy. Commit `78bead5`.
- **Sprint 28**: consolidación del modelo RBAC/tenant/SUPERADMIN introducido y modificado en Sprints
  22-27, corrección del alcance real de `SUPPORT_SESSION` frente al diseño original de
  `ADR-RBAC-001`, cierre del gap frontend de creación de usuario por SUPERADMIN (backend ya lo
  soportaba desde Sprint 27, el formulario no lo exponía), y reconciliación de esta documentación con
  el estado real del código y de `git log`.

## Regla de progreso

No se considera completada una fase hasta cumplir sus criterios de aceptación y pruebas.
