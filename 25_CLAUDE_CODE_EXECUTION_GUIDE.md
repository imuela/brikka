# BRIKA — CLAUDE CODE EXECUTION GUIDE V1

## 0. Estado

Este documento es el **único plan de ejecución sprint a sprint autoritativo** para Claude Code (`ADR-PROCESS-001`).

`14_IMPLEMENTATION_PLAN.md` queda `SUPERSEDED` por este documento. `09_ROADMAP.md` es el mapa macro para negocio/stakeholders y se referencia contra los sprints definidos aquí.

## 1. Fuente de verdad

Claude Code debe considerar como fuente de verdad, en este orden:
1. BRIKA_MASTER_SPEC.md
2. especificaciones funcionales
3. ERD (`15_DEFINITIVE_ERD.md`)
4. PostgreSQL schema (`16_POSTGRESQL_SCHEMA_SPECIFICATION.md`)
5. API (`17_API_SPECIFICATION_DETAILED.md`)
6. Security (`06_SECURITY_SPECIFICATION.md`)
7. Portal Cliente
8. decisiones/traceability (`12_DECISION_LOG.md`, `08_REQUIREMENTS_TRACEABILITY.md`)

Si existe contradicción, detenerse y señalarla. No decidir por su cuenta.

## 2. No improvisar

No:
- inventar entidades;
- cambiar stack;
- introducir microservicios sin decisión;
- dar acceso SQL directo a IA ni al Python Worker;
- eliminar tenant checks;
- exponer datos internos al Portal Cliente;
- mezclar `RBAC permission` con `entitlement` de plan;
- omitir `conversation_participants` en conversaciones tipo CLIENT.

## 3. Orden de implementación

### Sprint 0 — Infraestructura y entorno (`ADR-PROCESS-002`)

Incluye:
- repositorio y estructura inicial (Angular + Spring Boot);
- Docker y Docker Compose;
- contenedor PostgreSQL **vacío, sin esquema**;
- contenedor RabbitMQ;
- almacenamiento S3-compatible local;
- OIDC/Identity local;
- health checks;
- CI;
- lint/format;
- documentación técnica mínima necesaria (`.env.example`, README de arranque).

**No incluye:** esquema definitivo de PostgreSQL, migraciones de negocio, RBAC funcional, lógica de negocio, APIs funcionales, frontend funcional.

Al finalizar Sprint 0, una máquina nueva debe poder levantar el entorno completo (contenedores arriba, health checks en verde) sin que exista todavía ninguna tabla de negocio.

### Sprint 1 — Flyway + foundation backend

- Primera migración Flyway (`V1__initial_schema.sql` en adelante) contra el PostgreSQL de Sprint 0;
- estructura base del backend Spring Boot (package-by-feature);
- estructura base del frontend Angular (core/auth/shared/features);
- observabilidad mínima (logs estructurados, correlation id, health endpoints de aplicación).

No incluye todavía lógica de negocio ni RBAC funcional.

### Sprint 2 — Identity + Tenant + RBAC

**`users.company_id` (`ADR-IDENTITY-001`, resuelto en fase previa a Sprint 2 mediante `V8__users_company_id_nullable.sql`):** nullable únicamente para `SUPERADMIN` (`company_id = NULL`); obligatorio en aplicación para MANAGER/BROKER/CLIENT. No existe "empresa plataforma" ni tabla separada para SUPERADMIN. Índice `uq_users_email_no_company (email) WHERE company_id IS NULL` complementa a `uq_users_company_email` para evitar duplicados de email entre SUPERADMIN.

- companies, users, roles, permissions, user_roles, role_permissions;
- `plans`, `entitlements`, `plan_entitlements`, `company_subscriptions` (`ADR-PLATFORM-001`) — la gestión de empresa/plataforma vive aquí porque es prerrequisito de cualquier feature limitada por plan;
- autenticación (OAuth/OIDC), autorización (permission **y** entitlement), TenantContext;
- tests de aislamiento de tenant desde el primer momento.

**`role_permissions` (`ADR-RBAC-001`):** sembrar exactamente las 221 combinaciones `APPROVED` de la matriz definitiva (81 SUPERADMIN + 71 MANAGER + 58 BROKER + 11 CLIENT). No sembrar ninguna combinación `PENDING` (16, todas de IA) ni `NOT_ASSIGNED`.

**`TenantContext` — regla obligatoria desde el primer commit:** para `SUPERADMIN`, `TenantContext` no resuelve ningún `company_id` salvo mediante `SUPPORT_SESSION` activa. Como `SUPPORT_SESSION` no se implementa en Sprint 2 (ver abajo), esto significa en la práctica que `SUPERADMIN` no resuelve tenant en ningún caso durante este sprint — comportamiento seguro por defecto, no una limitación temporal a corregir con un bypass.

**`SUPPORT_SESSION` — explícitamente fuera de Sprint 2.** No se implementa la entidad `support_sessions`, ni endpoints de apertura/cierre, ni la columna `support_session_id` en `audit_events`. Ningún endpoint puede consumir un permiso marcado `SUPPORT_SESSION` en la matriz de `ADR-RBAC-001` hasta que ese mecanismo exista completo. Sprint de implementación: **pendiente de asignación explícita** (recomendación no vinculante: no más tarde de Sprint 11 — Hardening).

**IA (`AI_USE`, `AI_DOCUMENT_ANALYZE`, `AI_SUMMARIZE`, `AI_DRAFT_MESSAGE`, y para MANAGER/BROKER también `AI_MANAGE_CONFIGURATION`/`AI_READ_USAGE`):** los 16 permisos quedan `PENDING` en `ADR-RBAC-001`. No sembrar, no implementar ningún endpoint que los consuma hasta que exista una decisión de producto sobre alcance de IA por rol — a resolver antes de Sprint 10.

### Sprint 3 — CRM + CASE + Workflow

- clients, client_portal_accounts;
- cases, case_clients, case_assignments, case_status_history;
- transiciones de estado según `13_DEFINITIVE_WORKFLOW_SPECIFICATION.md`;
- `activities` (`ADR-AUDIT-001`) — se introduce aquí porque los primeros eventos de dominio (`CaseCreated`, `CaseStatusChanged`) ya existen desde este sprint.

### Sprint 4 — Property + Documents + Storage

- properties;
- document_types, `document_requirements` (`ADR-DOC-001`), document_requests (con `requirement_id`), documents, document_versions, document_publications;
- integración con Object Storage (incluye el patrón de key para documentos formales de `18_STORAGE_SPECIFICATION.md`);
- revisión/aprobación/rechazo de documentos.

### Sprint 5 — Financing + Banks + Bank Contacts

- simulations, financing_requests;
- banks, bank_products, bank_criteria_versions;
- bank_contacts (aislados por tenant, snapshot histórico).

### Sprint 6 — Bank Requests + Responses + Offers

- bank_requests (con `contact_snapshot`), bank_responses, bank_offers, final_financing;
- motor determinista de matching (`06_BANK_ENGINE_SPECIFICATION.md`, `22_BANK_ENGINE_DETAILED.md`).

### Sprint 7 — Portal Cliente

- autenticación de Portal Cliente (separada de usuarios internos);
- dashboard, visibilidad de operación publicada;
- document_publications aplicadas al Portal;
- mensajería CLIENT con `conversation_participants` obligatorio (`ADR-COMMS-002`) — evaluación `tenant + case + participant + visibility` en cada endpoint `/portal/*`.

### Sprint 8 — Tasks + Communications + Notifications

- tasks;
- conversations, `conversation_participants`, messages, `message_attachments` (`ADR-COMMS-001`);
- notifications, `notification_deliveries` con workers `IN_APP` y `EMAIL` únicamente (`ADR-NOTIF-001`); `PUSH`/`SMS` quedan como valores de catálogo sin worker.

### Sprint 9 — Scoring

- scoring_rulesets, scoring_rules, scoring_results;
- cálculo reproducible y explicable.

### Sprint 10 — AI Gateway + Integrations

- AI Gateway/Orchestrator;
- Python Worker de extracción documental, desplegado con aislamiento de red respecto a PostgreSQL (`ADR-AI-001`); `document_extractions`, `ai_usage`;
- casos de uso IA V1 (`21_AI_V1_SCOPE.md`);
- `integrations` como catálogo mínimo de solo lectura/estado (`ADR-INTEGRATIONS-001`) — sin adapters concretos.

### Sprint 11 — Audit + Reporting + Hardening

- audit_events (distinto de `activities`, ya introducido en Sprint 3 — `ADR-AUDIT-001`);
- reporting/exports;
- hardening de seguridad, revisión de RLS.

### Sprint 12 — E2E + Security + Performance + Release

- pruebas E2E completas;
- revisión de seguridad final (incluye verificación de aislamiento de red del Python Worker);
- pruebas de carga en áreas críticas;
- preparación de despliegue.

## 4. Cada sprint

Debe producir:
- código;
- tests;
- migraciones;
- documentación actualizada;
- criterios de aceptación cumplidos.

## 5. Regla de parada

Si una decisión necesaria no está documentada, Claude Code debe:
1. identificarla;
2. explicar impacto;
3. solicitar decisión;
4. no inventar una solución estructural.

## 6. Correspondencia con el roadmap macro

Ver tabla de correspondencia Fase ↔ Sprint en `09_ROADMAP.md`.
