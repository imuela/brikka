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

### Extensión del roadmap — Frontend V1 (`ADR-PROCESS-004`)

El plan original terminaba en el Sprint 12 (backend, "Release"). El objetivo del proyecto pasó a ser una V1 funcional y usable completa de Brika, no detener el desarrollo artificialmente en el Sprint 12 — ver `ADR-PROCESS-004` para el contexto completo. Los sprints siguientes se definen incrementalmente, uno a uno, siguiendo el mismo proceso (Fase 0 → opciones candidatas basadas en backend ya existente → decisión explícita del responsable del proyecto), nunca por adelantado.

### Sprint 13 — Frontend foundation (`ADR-FRONTEND-001`)

- estructura base Angular (core/auth/shared/features), zoneless, standalone;
- OIDC/PKCE contra Keycloak (realm `brika` versionado en `keycloak/brika-realm.json`, importado automáticamente);
- `CorsConfigurationSource` mínimo en backend (bloqueante detectado en Fase 0, D1 de `ADR-FRONTEND-001`);
- shell autenticado (layout, sesión, menú de usuario) que prueba el pipeline auth/sesión end to end;
- Angular Material como librería UI (D4 de `ADR-FRONTEND-001`).

### Sprint 14 — CRM + Operaciones (frontend)

- Clientes: listado, detalle, alta/edición;
- Casos: listado, detalle, alta/edición, cambio de estado, cancelación, reapertura, asignación, gestión de clientes del caso;
- gating por `permissionGuard`/`*appHasPermission` contra el catálogo RBAC real (Sprint 2), nunca inventado.

### Sprint 15 — Inmueble + Documentación (frontend) + Auditoría UX/i18n

- Inmueble: registro/edición sobre el caso;
- Documentos: creación, subida de versión, revisión, publicación/despublicación, descarga, historial de versiones;
- Solicitudes de documentación: creación, cumplimentación, cancelación — `document-requirements` estrictamente de solo lectura (sin CRUD de catálogo);
- gap-fix mínimo autorizado: `GET /api/v1/document-types`;
- auditoría UX/i18n/presentación pre-Sprint 16: `LOCALE_ID` es-ES, etiquetas centralizadas de estados/roles, traducción centralizada de errores de API, formatos españoles (fecha/moneda), tablas responsive, confirmación antes de acciones destructivas (`removeClient`).

### Sprint 16 — Financing / Simulations + Bank Matching / Ofertas (frontend) (`ADR-PROCESS-004`)

- Información financiera de la operación: `FinancingRequest` (importe solicitado, plazo) — `GET`/`POST` anidados bajo el caso, `PATCH` standalone;
- Simulación hipotecaria: `Simulation` — herramienta interna no vinculante, distinta de la financiación finalmente concedida (`FUNCTIONAL_SPECIFICATION.md` §10);
- Matching bancario: `BankMatchingController` (ejecutar/listar/consultar resultado) sobre el catálogo de bancos/criterios ya existente (Sprint 5); `BankMatchOverrideController` (corrección manual de un resultado de regla, MANAGER/SUPERADMIN únicamente);
- Solicitudes a banco → respuestas → ofertas → financiación final: `BankRequestController` (crear solicitud, registrar respuesta, crear oferta), `BankOfferController` (listar/consultar ofertas, seleccionar oferta final);
- ningún endpoint, entidad, migración ni permiso nuevo — únicamente frontend sobre capacidades de backend ya operativas desde los Sprints 5, 6A y 6B;
- fuera de alcance: Portal Cliente, Tasks/Communications/Notifications, administración (Users/Companies/Plans) — bloques independientes posteriores.

### Sprint 17 — Tasks + Comunicaciones internas + Notificaciones (frontend) (`ADR-PROCESS-005`)

- Tareas: sección embebida en `case-detail` (con y sin `caseId`, filtrado client-side de `GET /api/v1/tasks` — no existe endpoint case-scoped dedicado) y vista tenant-wide en el shell (`/app/tasks`); alta/listado/edición/asignación/completado (`/complete`)/eliminación (`TASK_DELETE`, MANAGER/SUPERADMIN únicamente, ausente en BROKER);
- Comunicaciones: sección embebida en `case-detail`; conversaciones INTERNAL y CLIENT (`ConversationController`), gestión de participantes CLIENT (`CONVERSATION_PARTICIPANT_MANAGE`), listado/lectura/envío de mensajes (`MessageController`), adjuntos por mensaje (subida/descarga vía `MessageAttachmentController`, carga perezosa por mensaje); conversaciones SYSTEM explícitamente no implementadas (backend nunca las produce);
- Notificaciones: entrada de navegación "Notificaciones" en el shell (`/app/notifications`), listado de notificaciones propias del usuario autenticado, marcar como leída; sin productor conectado en el backend (ningún módulo de dominio escribe en `notifications` todavía) — la UI muestra honestamente el estado vacío real, sin datos ni productores inventados;
- ningún endpoint, entidad, migración ni permiso nuevo — únicamente frontend sobre capacidades de backend ya operativas desde el Sprint 8;
- confirmado en este sprint (no nuevo): la arquitectura de entrega asíncrona por RabbitMQ descrita en `ADR-NOTIF-001` no está implementada — la escritura de `Notification`/`NotificationDelivery` es 100% síncrona e in-process allí donde existe, y no hay ningún productor de dominio conectado (ver `12_DECISION_LOG.md`, `ADR-PROCESS-005`);
- fuera de alcance: Portal Cliente (Sprint 19), administración (Users/Companies/Plans, Sprint 18 candidato), Entitlements, cualquier productor de notificaciones nuevo.

### Sprint 18 — Administración: Users + Companies + Plans + Subscriptions (frontend) (`ADR-PROCESS-006`)

- Usuarios: vista tenant-wide en el shell (`/app/users`, `GET /api/v1/users`), alta (`USER_CREATE`, incluye `externalIdentityId` manual — sin aprovisionamiento Keycloak), edición (`USER_UPDATE`, solo `firstName`/`lastName`, fiel a `UpdateUserApiRequest`), deshabilitar (`USER_DISABLE`, sin reactivación — no existe endpoint); rol limitado a MANAGER/BROKER en el alta (SUPERADMIN es rechazado incondicionalmente por `UserProvisioningService`, CLIENT se aprovisiona exclusivamente vía Portal Cliente); `USER_ASSIGN_ROLE` confirmado sembrado sin ningún endpoint que lo use — deuda técnica documentada, no implementada;
- Empresas: vista `/app/companies` (`GET /api/v1/companies` — GLOBAL para SUPERADMIN, TENANT/propia empresa para MANAGER, ya filtrado por el backend), alta (`COMPANY_CREATE`, SUPERADMIN-only), detalle+edición (`COMPANY_READ`/`UPDATE`), suspender/eliminar (`COMPANY_SUSPEND`/`DELETE`, SUPERADMIN-only, DELETE es transición lógica a `DELETED`, nunca borrado físico);
- Suscripción: sección embebida en el detalle de empresa (no pantalla ni nav propios — el contrato real solo la expone anidada bajo `/companies/{id}/subscription`), lectura/asignación/cambio de plan (`PUT`, upsert real) y cancelación (`POST .../cancel`), gated por `SUBSCRIPTION_READ`/`MANAGE` (SUPERADMIN-only) — primer caso del proyecto donde una sección embebida es genuinamente inalcanzable para el visor (MANAGER en su propia empresa), resuelto sin workaround: la petición ni se dispara sin el permiso, evitando un 403 espurio en el error de página;
- Planes: catálogo global SUPERADMIN-only `/app/plans` (`GET`/`POST`/`PATCH /api/v1/plans`), patrón de diálogo igual que Bancos (Sprint 16); `status` es texto libre (sin CHECK ni catálogo documentado en el backend, igual que `tasks.type`);
- Entitlements: confirmado sin ningún endpoint (`EntitlementResolutionService` sin controller, sin permiso `ENTITLEMENT_*` siquiera sembrado) — completamente fuera de alcance, no se construye ninguna pantalla;
- confirmado en este sprint (no nuevo): Usuarios reproduce la misma limitación estructural de SUPERADMIN que Tareas/Comunicaciones (Sprint 17) — `requireTenant()` deniega sin `SUPPORT_SESSION`, verificado por test de integración dedicado (`superadminWithoutSupportSessionCannotAccessUsersEndpoint`); Empresas/Planes/Suscripciones son GLOBAL (sin `requireTenant()`), por lo que SUPERADMIN sí opera sin esa limitación ahí;
- ningún endpoint, entidad, migración ni permiso nuevo — únicamente frontend sobre capacidades de backend ya operativas desde los Sprints 2 y 12.1;
- fuera de alcance: Portal Cliente (Sprint 19), `SUPPORT_SESSION`, aprovisionamiento Keycloak, productores de notificaciones/RabbitMQ, cualquier corrección de la deuda técnica `USER_ASSIGN_ROLE`.

### Sprint 19 — Portal Cliente (frontend + 2 gaps de backend acotados) (`ADR-PROCESS-007`, `ADR-PORTAL-AUTH-001`)

- Realm Keycloak `brika-portal` provisionado (`keycloak/brika-portal-realm.json`, client `brika-portal-frontend`, usuario de demostración `demo.client`), montado en `docker-compose.yml` junto al realm interno `brika`, sin afectarlo;
- `PortalAuthService`/`PortalSessionStore`/`PortalSessionService` independientes (Authorization Code + PKCE contra `brika-portal`, tokens solo en memoria) — nunca comparten estado con `AuthService`/`SessionStore` internos; `portalAuthInterceptor`/`authInterceptor` particionan el tráfico HTTP por URL (`/api/v1/portal/**` vs el resto) sin solapamiento;
- Dashboard (`/portal`): casos propios (`GET /api/v1/portal/cases`) y notificaciones propias, con marcado de leída;
- Detalle de operación (`/portal/cases/:id`): datos del caso, documentos publicados, solicitudes de documentación (vista explícita nueva, ver más abajo), subida de documento ligada a una solicitud concreta, mensajería CLIENT con adjuntos (carga perezosa por mensaje);
- Perfil (`/portal/profile`): lectura/edición de email y teléfono (`GET`/`PATCH /api/v1/portal/profile`);
- **2 gaps de backend cerrados, excepción explícita y acotada autorizada en Fase 2 (`ADR-PROCESS-007`):** `PATCH /api/v1/portal/notifications/{id}/read` (marcar notificación propia como leída) y `GET /api/v1/portal/cases/{id}/document-requests` (vista explícita de solicitudes de documentación del cliente, con nombre de tipo de documento resuelto) — ambos reutilizan permisos ya sembrados (`PORTAL_NOTIFICATION_READ`, `PORTAL_DOCUMENT_REQUEST_RESPOND`), sin inventar ninguno nuevo, y sin sustituir la heurística de auto-cumplimiento existente al subir un documento;
- ningún endpoint fuera de esos 2 gaps, ninguna migración, ningún permiso nuevo — el resto del backend Portal (11 endpoints) ya existía y estaba operativo desde el Sprint 7;
- fuera de alcance: `SUPPORT_SESSION`, cualquier productor de notificaciones/RabbitMQ, Entitlements — el proyecto no tiene ningún bloque de Fase L pendiente tras este sprint.

### Sprint 20 — Rebranding Brikka + imagen de marca + normalización de textos (`ADR-PROCESS-008`)

- Marca visible del producto cambiada de "Brika" a "Brikka" en los 5 puntos donde aparecía (título del navegador, login interno, login Portal, cabecera interna, cabecera Portal) — identificadores técnicos (paquetes Java, client IDs Keycloak, nombres de realm, comentarios de código sobre el contrato de API) deliberadamente no renombrados, documentado como excepción explícita;
- `docs/branding/BRIKKA_BRAND_GUIDELINES.md` y `docs/branding/BRIKKA_BRAND_REVIEW.md` (nuevos): identidad analizada a partir de la única referencia visual existente (`docs/branding/file_0000000080d08243a1a6c92157dc0259.png`), documentada, **pendiente de aprobación humana** — colores extraídos por muestreo real de píxeles (alta confianza para azul/navy/blanco, baja confianza para verde/ámbar de estado, rojo no extraído/propuesto), tipografía y trazado del símbolo son inferencia, no extracción; paleta Material y tipografía de la aplicación **no** retemadas en este sprint (fuera de alcance, pendiente de decisión aparte);
- `docs/branding/assets/` (nuevo): 8 activos SVG (logo primario/oscuro/claro/monocromo/vertical/símbolo/favicon) + 1 PNG rasterizado (512×512, generado vía canvas del navegador — no había ninguna herramienta de rasterización de imágenes disponible en el entorno); favicon de la aplicación (`frontend/public/favicon.svg`, con `.ico` de 32×32 como *fallback*) sustituido a partir de estos activos;
- auditoría completa de textos visibles del frontend (incluido Portal Cliente Sprint 19 y administración Sprint 18): la infraestructura de traducción ya existente (`status-labels.ts`/`StatusLabelPipe`/`friendlyErrorMessage`) ya cubría la inmensa mayoría de la aplicación; se cerraron los huecos reales encontrados — filtraciones de código técnico sin traducir en `case-detail`, `case-list`, `portal-case-detail` y `portal-dashboard` (los 4 campos "Tipo" recatalogados en este mismo sprint, ver más abajo);
- **4 campos "Tipo" convertidos de texto libre a desplegable** (`OPERATION_TYPES`, `PROPERTY_TYPES`, `ASSIGNMENT_TYPES`, `TASK_TYPES` en sus respectivos `*.model.ts`, etiquetas en `status-labels.ts`): tipo de operación, tipo de inmueble, tipo de asignación, tipo de tarea. **Ninguno de los 4 tenía un conjunto cerrado real en el backend** (los tres primeros documentados explícitamente como "texto libre, sin catálogo" desde el Sprint 3/14; el cuarto desde el Sprint 17) — los catálogos son una decisión de producto **aprobada explícitamente por el responsable del proyecto durante este sprint**, no una extracción de ningún enum/CHECK/constante preexistente, y se documentan como tal (nunca presentados como "ya existían");
- migración de datos `V16__normalize_operation_type_seed_data.sql`: el único valor de `operation_type` usado en toda la base de datos de desarrollo (`MORTGAGE`) se corrige a `PURCHASE` para ser válido bajo el nuevo catálogo — sin cambio de esquema, sin `CHECK` constraint añadido (el backend sigue aceptando cualquier texto; solo el frontend impone el catálogo cerrado);
- fuera de alcance: auditoría general del proyecto (será el sprint siguiente), refactor arquitectónico, nuevas funcionalidades/entidades/permisos, retemado visual completo (color/tipografía) de la aplicación, catálogo de `notification.type` (sin productor real conectado todavía, inventar una traducción sería especulativo), catálogo de `plan.status` (texto libre por diseño explícito, mismo patrón que los 4 campos Tipo antes de esta decisión), catálogo de `BankMatchRuleResult.field`/`.operator` (claves técnicas del motor de reglas, visibles solo a broker/manager que ya trabajan con el JSON de criterios sin traducir en la misma pantalla).

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
