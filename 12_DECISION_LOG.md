# BRIKA — DECISION LOG V1

## ADR-001 — SaaS multiempresa
**Estado:** DECIDIDO  
Brika será multi-tenant.

## ADR-002 — Público objetivo inicial
**Estado:** DECIDIDO  
V1 se orienta a brokers hipotecarios.

## ADR-003 — Portal Cliente
**Estado:** DECIDIDO  
Existirá un Portal Cliente independiente y controlado por el broker.

## ADR-004 — Roles
**Estado:** DECIDIDO  
SUPERADMIN, MANAGER, BROKER y CLIENT.

## ADR-005 — Backend
**Estado:** DECIDIDO  
Java + Spring Boot.

## ADR-006 — Frontend
**Estado:** DECIDIDO  
Angular + TypeScript.

## ADR-007 — Base de datos
**Estado:** DECIDIDO  
PostgreSQL.

## ADR-008 — Migraciones
**Estado:** DECIDIDO  
Flyway.

## ADR-009 — Documentos
**Estado:** DECIDIDO  
Modelo versionado DOCUMENT → VERSION → FILE.

## ADR-010 — Tenant isolation
**Estado:** DECIDIDO  
Obligatorio y aplicado en múltiples capas.

## ADR-011 — Scoring
**Estado:** DECIDIDO  
Scoring explicable y separado de la decisión bancaria.

## ADR-012 — IA
**Estado:** DECIDIDO  
AI Gateway desacoplado del proveedor.

## ADR-013 — Auditoría
**Estado:** DECIDIDO  
Acciones sensibles deben ser trazables.

## ADR-014 — Regla de cambio
**Estado:** DECIDIDO  
Los cambios de arquitectura/reglas deben documentarse antes de implementarse.


## ADR-BANK-001 — Propiedad de contactos bancarios

**Decisión:** `BANK` será global y único en Brika. `BANK_CONTACT` pertenecerá a `COMPANY`, no al broker individual.

**Motivo:** una empresa puede tener varios brokers y debe poder compartir sus contactos bancarios internamente. Los contactos son información operativa propia de cada empresa y no deben exponerse a otras empresas.

**Consecuencia:** el aislamiento de tenant se aplica a `BANK_CONTACT`; las solicitudes bancarias conservarán el contacto utilizado y/o su snapshot histórico.

---

## Cierre de arquitectura y documentación — ADRs de la segunda auditoría

Los siguientes ADR resuelven las inconsistencias detectadas en la auditoría cruzada de documentación (ver `11_CROSS_DOCUMENT_REVIEW.md` y la revisión posterior). Todos quedan **APPROVED** por decisión explícita antes de iniciar Sprint 0.

## ADR-DOC-001 — Document Requirements y File

**Contexto:** `BRIKA_MASTER_SPEC.md` §10 define la cadena `DOCUMENT TYPE → REQUIREMENT → REQUEST → DOCUMENT → VERSION → FILE`. `03_DOMAIN_SPECIFICATION.md` §2 lista `DocumentRequirement` y `File` como entidades conceptuales. El ERD/PostgreSQL definitivos (`15`, `16`) no incluían `REQUIREMENT` como tabla y fusionaban `FILE` dentro de `DOCUMENT_VERSION` sin registrar la decisión.

**Problema:** Sin `DOCUMENT_REQUIREMENT` no es posible determinar automáticamente qué documentación es necesaria para una operación, tal como exige `FUNCTIONAL_SPECIFICATION.md` §11. La fusión de `FILE` no estaba documentada y contradecía `03_DOMAIN_SPECIFICATION.md` sin resolución.

**Decisión:** Reinstaurar `document_requirements` como catálogo versionable de reglas condicionadas (`conditions jsonb`), preparado para depender de tipo de operación, perfil de cliente, banco, producto u otras condiciones futuras sin cambio de esquema. Añadir `requirement_id` nullable en `document_requests` para trazar el origen de cada solicitud. Ratificar que `File` no tiene tabla propia: sus atributos quedan formalmente absorbidos en `document_versions`.

**Alternativas consideradas:**
(a) No modelar `REQUIREMENT` y resolver "documentación necesaria" solo con lógica de aplicación no versionada — descartada por romper la trazabilidad exigida por `07_DATA_GOVERNANCE_SPECIFICATION.md`.
(b) Reinstaurar `files` como tabla independiente de `document_versions` — descartada por no existir ningún caso de uso documentado de una versión con múltiples ficheros.

**Consecuencias:** nueva tabla `document_requirements` y FK `requirement_id`; nuevo permiso de gestión de catálogo; `03_DOMAIN_SPECIFICATION.md` deja de listar `File` como entidad independiente.

**Documentos afectados:** `03_DOMAIN_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`, `14_DEFINITIVE_PERMISSION_CATALOG.md`, `08_REQUIREMENTS_TRACEABILITY.md`.

**Estado:** APPROVED.

## ADR-PLATFORM-001 — Plans / Entitlements / Plan Entitlements / Company Subscriptions

**Contexto:** `BRIKA_MASTER_SPEC.md` §4.1 y `FUNCTIONAL_SPECIFICATION.md` §4 asignan a SUPERADMIN la gestión de planes, suscripciones y consumo/funcionalidades por empresa. El esquema PostgreSQL definitivo no incluía ninguna tabla de plataforma para representarlo.

**Problema:** No existía forma física de representar "empresa X tiene plan Y con funcionalidades Z", ni de diferenciar autorización por rol de autorización por plan contratado.

**Decisión:** Reinstaurar `plans`, `entitlements`, `plan_entitlements` y `company_subscriptions`. Se diferencian explícitamente cuatro conceptos: estado de empresa (`companies.status`), estado de suscripción (`company_subscriptions.status`), permiso RBAC y entitlement de plan. La autorización efectiva para funcionalidades limitadas por plan se evalúa como `tenant + RBAC permission + entitlement`. Facturación y pago automático quedan explícitamente fuera de V1 (PENDIENTE, ver `BRIKA_MASTER_SPEC.md` §18).

**Alternativas consideradas:**
(a) No modelar planes en V1 y tratarlo como configuración estática en código — descartada, contradice explícitamente `MASTER_SPEC`/`FUNCTIONAL_SPEC`.
(b) Integrar pasarela de pago ya en V1 — descartada, ninguna documentación funcional lo exige y añade superficie de riesgo financiero no aprobada.

**Consecuencias:** nueva capa de autorización (permiso **y** entitlement, no solo permiso) sobre el pipeline de autorización existente; nuevas pantallas SUPERADMIN de gestión de planes/suscripciones.

**Documentos afectados:** `03_DOMAIN_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `14_DEFINITIVE_PERMISSION_CATALOG.md`, `17_API_SPECIFICATION_DETAILED.md`, `06_SECURITY_SPECIFICATION.md`, `BRIKA_MASTER_SPEC.md`.

**Estado:** APPROVED.

## ADR-INTEGRATIONS-001 — Integraciones

**Contexto:** `05_API_SPECIFICATION.md` y `14_DEFINITIVE_PERMISSION_CATALOG.md` ya referenciaban `/integrations` e `INTEGRATION_*`; `BRIKA_MASTER_SPEC.md` §17 incluye "integraciones base" en el alcance V1. El esquema definitivo no tenía tabla.

**Problema:** No hay ninguna integración externa concreta definida en V1; construir un sistema genérico completo de integraciones sería sobreingeniería sin caso de uso.

**Decisión:** Reinstaurar únicamente `integrations` como estructura mínima de extensibilidad (catálogo de integraciones configuradas, sin lógica funcional de ejecución). `integration_events` **no** se desarrolla en V1 salvo que surja una dependencia técnica real y se registre un ADR específico para ello. No se introduce ningún proveedor externo concreto no aprobado.

**Alternativas consideradas:**
(a) Eliminar `/integrations` y los permisos `INTEGRATION_*` de toda la documentación — descartada, `MASTER_SPEC` ya compromete "integraciones base" como parte del alcance V1.
(b) Construir `integrations` + `integration_events` + adapters completos ya — descartada por sobredimensionar sin caso de uso concreto.

**Consecuencias:** `/integrations` queda de solo lectura/estado en V1; no se implementa ningún adapter concreto; `integration_events` queda documentado como PENDIENTE condicionado.

**Documentos afectados:** `03_DOMAIN_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `05_API_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`, `BRIKA_MASTER_SPEC.md`.

**Estado:** APPROVED.

## ADR-AUDIT-001 — Activities vs Audit Events

**Contexto:** `FUNCTIONAL_SPECIFICATION.md` §19 declara explícitamente que la actividad funcional es distinta del audit log técnico. El esquema definitivo solo incluía `audit_events`.

**Problema:** No existía tabla para alimentar los dashboards de "actividad reciente" (`FUNCTIONAL_SPECIFICATION.md` §3) sin reutilizar el log de auditoría de seguridad, que tiene control de acceso distinto (`AUDIT_READ`) y una audiencia distinta (cumplimiento vs UI de negocio).

**Decisión:** Mantener `audit_events` (seguridad/cumplimiento, inmutable, acceso restringido por `AUDIT_READ`) y `activities` (timeline funcional de negocio, autorización estándar por recurso, p. ej. `CASE_READ`) como tablas independientes. Ambas se alimentan de los mismos eventos de dominio (RabbitMQ) mediante consumidores separados. Ninguna es una vista derivada de la otra.

**Alternativas consideradas:** proyectar `activities` como vista filtrada de `audit_events` — descartada porque limita el control de acceso granular y mezcla dos audiencias con requisitos de seguridad distintos.

**Consecuencias:** dos consumidores de eventos de dominio en vez de uno; `activities` puede incluir eventos que no son auditables por seguridad (p. ej. "tarea completada").

**Documentos afectados:** `03_DOMAIN_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `20_RABBITMQ_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`.

**Estado:** APPROVED.

## ADR-COMMS-001 — Message Attachments

**Contexto:** `FUNCTIONAL_SPECIFICATION.md` §14 y el permiso `MESSAGE_ATTACHMENT_UPLOAD` (`14_DEFINITIVE_PERMISSION_CATALOG.md` §12) prometen adjuntos en mensajes; no existía tabla de soporte.

**Decisión:** Crear `message_attachments`, independiente del pipeline formal `DOCUMENT` (que exige tipo/requisito/revisión), reutilizando las reglas de storage, checksum, MIME y tamaño definidas en `18_STORAGE_SPECIFICATION.md`.

**Alternativas consideradas:** forzar todo adjunto de chat a pasar por el pipeline `DOCUMENT` — descartada por sobrecargar un flujo pensado para documentación formal con revisión, con un caso de uso (adjuntos de conversación) que no la necesita.

**Consecuencias:** nuevo patrón de storage key para adjuntos de conversación, análogo al de documentos.

**Documentos afectados:** `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `18_STORAGE_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`, `14_DEFINITIVE_PERMISSION_CATALOG.md`.

**Estado:** APPROVED.

**Adenda (cierre final pre-Sprint 0):** se confirma explícitamente que `message_attachments` cubre también las conversaciones del Portal Cliente, con la misma tabla y el mismo patrón de storage que las conversaciones internas — no se crea entidad ni tabla nueva. El acceso exige, en orden: tenant → caso → `conversation_participant` → visibility → permiso (`PORTAL_MESSAGE_ATTACHMENT_UPLOAD`/`PORTAL_MESSAGE_READ`), más las mismas validaciones de storage (MIME, tamaño, checksum, descarga mediada por backend, nunca exposición directa del storage). No se convierten en `DOCUMENT` del pipeline formal. No requiere ADR propio: es una aclaración de alcance de `ADR-COMMS-001`, no una decisión estructural nueva. Documentos actualizados: `07_PORTAL_CLIENTE.md`, `06_SECURITY_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`, `14_DEFINITIVE_PERMISSION_CATALOG.md`, `18_STORAGE_SPECIFICATION.md`.

## ADR-COMMS-002 — Conversation Participants

**Contexto:** `15_DEFINITIVE_ERD.md` §12 ya exigía considerar "participantes" en la autorización de conversaciones, pero no existía tabla que los modelara.

**Problema:** Un `CASE` puede tener varios `CaseClient` (HOLDER/CO_HOLDER/GUARANTOR); sin participantes no hay forma de restringir qué cliente(s) del Portal ven una conversación tipo `CLIENT` concreta cuando hay más de un titular.

**Decisión:** Crear `conversation_participants`, obligatoria para conversaciones tipo `CLIENT`. La autorización backend debe comprobar como mínimo `tenant + case + participant + visibility`; nunca basta con verificar que el cliente pertenece a la empresa. El frontend nunca se considera frontera de seguridad. La visibilidad del Portal Cliente sigue controlada por el backend y por las reglas de publicación de Brika (no por la mera pertenencia al `CASE`).

**Alternativas consideradas:** restringir solo por tipo de conversación + tenant — descartada, insuficiente cuando hay varios clientes en el mismo caso con necesidades de privacidad distintas.

**Consecuencias:** refuerzo explícito en `06_SECURITY_SPECIFICATION.md` y `07_PORTAL_CLIENTE.md` de la regla de autorización de mensajería; para conversaciones tipo `INTERNAL` la restricción sigue siendo implícita vía `CASE_ASSIGNMENT` en V1 (no requiere fila de participante).

**Documentos afectados:** `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `06_SECURITY_SPECIFICATION.md`, `07_PORTAL_CLIENTE.md`, `17_API_SPECIFICATION_DETAILED.md`.

**Estado:** APPROVED.

## ADR-NOTIF-001 — Notifications / Canales

**Contexto:** `FUNCTIONAL_SPECIFICATION.md` §20 prometía canales `IN_APP/EMAIL/PUSH/SMS`; no existía separación entre notificación lógica y entrega por canal.

**Decisión:** V1 implementa únicamente `IN_APP` y `EMAIL`. Se crea `notification_deliveries` para separar la notificación lógica (`notifications`) de cada entrega por canal, con estado independiente por canal. La arquitectura queda preparada para añadir `PUSH`/`SMS` sin cambio estructural (el `channel` ya es un valor de catálogo, no una columna fija), pero no se implementan proveedores ni flujos funcionales de `PUSH`/`SMS` en V1.

**Alternativas consideradas:** implementar los 4 canales en V1 — descartada, ningún proveedor de `PUSH`/`SMS` está aprobado ni contratado, y añadirlos sin proveedor real generaría funcionalidad simulada.

**Consecuencias:** workers de entrega por canal (`IN_APP`, `EMAIL`) consumiendo `notification.requested` vía RabbitMQ.

**Documentos afectados:** `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `20_RABBITMQ_SPECIFICATION.md`, `FUNCTIONAL_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`.

**Estado:** APPROVED.

## ADR-PROCESS-001 — Roadmap único

**Contexto:** existían tres documentos de planificación (`09_ROADMAP.md`, `14_IMPLEMENTATION_PLAN.md`, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`) sin remitirse entre sí, con riesgo de contradicción sobre el orden real de ejecución.

**Decisión:** `25_CLAUDE_CODE_EXECUTION_GUIDE.md` es el único documento autoritativo de ejecución sprint a sprint. `14_IMPLEMENTATION_PLAN.md` pasa a estado `SUPERSEDED` y se conserva como histórico (no se elimina, conforme a `CLAUDE.md` §3). `09_ROADMAP.md` permanece como roadmap macro para negocio/stakeholders, con una tabla de correspondencia explícita Fase ↔ Sprint respecto a `25`.

**Alternativas consideradas:** fusionar los tres documentos en uno solo eliminando dos — descartada, `CLAUDE.md` prohíbe eliminar versiones históricas de documentos sin política explícita.

**Consecuencias:** cualquier futura planificación de ejecución se edita únicamente en `25_CLAUDE_CODE_EXECUTION_GUIDE.md`.

**Documentos afectados:** `09_ROADMAP.md`, `14_IMPLEMENTATION_PLAN.md`, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `10_DOCUMENTATION_STATUS.md`.

**Estado:** APPROVED.

## ADR-PROCESS-002 — Alcance de Sprint 0

**Contexto:** `CLAUDE_CODE_START_PROMPT.md` y `26_PRE_CODING_AUDIT.md` incluían PostgreSQL/RabbitMQ/Storage/OIDC dentro de Sprint 0 sin distinguir infraestructura de lógica de negocio; `25_CLAUDE_CODE_EXECUTION_GUIDE.md` movía la base de datos a Sprint 1, generando una contradicción de alcance entre documentos que gobiernan la ejecución de Claude Code.

**Decisión:** Sprint 0 = infraestructura y entorno de desarrollo exclusivamente: repositorio, estructura inicial, Docker, Docker Compose, contenedor de PostgreSQL (vacío, sin esquema), contenedor de RabbitMQ, almacenamiento S3-compatible local, OIDC/Identity local, health checks, CI, lint/format, documentación técnica mínima necesaria. Sprint 0 **no** incluye esquema definitivo de PostgreSQL, migraciones de negocio, RBAC funcional, lógica de negocio, APIs funcionales ni frontend funcional. Sprint 1 = Flyway + foundation backend (primera migración ejecuta aquí). Sprint 2 = Identity + Tenant + RBAC.

**Alternativas consideradas:** mantener las dos definiciones de Sprint 0 en paralelo — descartada, generaba ambigüedad operativa real y riesgo de que Claude Code ejecutara migraciones de negocio antes de tiempo.

**Consecuencias:** ninguna migración de negocio se ejecuta hasta Sprint 1; ninguna lógica de autorización se implementa hasta Sprint 2.

**Documentos afectados:** `CLAUDE_CODE_START_PROMPT.md`, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `26_PRE_CODING_AUDIT.md`.

**Estado:** APPROVED.

## ADR-PROCESS-003 — README y snapshot de revisión

**Contexto:** `README.md` y `00_FINAL_REVIEW_README.md` tenían roles solapados y contenido contradictorio; `README.md` estaba desactualizado (no listaba los documentos 12–26).

**Decisión:** `README.md` es el índice vivo de toda la documentación del proyecto. `00_FINAL_REVIEW_README.md` es un snapshot fechado de una revisión concreta y no se mantiene actualizado tras esa fecha. `10_DOCUMENTATION_STATUS.md` es la única fuente del estado documental vigente.

**Alternativas consideradas:** eliminar uno de los dos ficheros — descartada, se prefiere reasignar roles antes que borrar histórico de revisión.

**Consecuencias:** ninguna consecuencia técnica; es una decisión de proceso documental.

**Documentos afectados:** `README.md`, `00_FINAL_REVIEW_README.md`, `10_DOCUMENTATION_STATUS.md`.

**Estado:** APPROVED.

## ADR-AI-001 — Python Worker / pgvector

**Contexto:** `03_TECHNICAL_SPECIFICATION.md` introducía un worker Python y pgvector como componentes auxiliares sin que `BRIKA_MASTER_SPEC.md` los reconociera como decisión de stack aprobada (§15/§20).

**Decisión:** Se ratifica un worker Python **stateless**, especializado en OCR/extracción/procesamiento documental, **sin acceso directo a PostgreSQL ni credenciales de PostgreSQL**, aislado también a nivel de red, invocable únicamente mediante AI Gateway/Orchestrator y/o RabbitMQ. Los resultados se persisten exclusivamente mediante mecanismos internos controlados por Spring Boot (`document_extractions`). `Python Worker → PostgreSQL` queda **PROHIBIDO**. `pgvector` se aprueba como extensión de la instancia PostgreSQL existente, no como base de datos independiente. No se implementan embeddings/RAG salvo que estén expresamente incluidos en un alcance V1 aprobado.

Arquitectura conceptual:
`Angular → Spring Boot → AI Gateway/Orchestrator → RabbitMQ → Python Worker → resultado → Spring Boot → PostgreSQL`

**Alternativas consideradas:** dar acceso directo a BD al worker Python por simplicidad de implementación — rechazada explícitamente, contradice el principio "la IA no accede directamente a la BD" ya vigente para el proveedor de IA (`21_AI_V1_SCOPE.md` §3), que debía extenderse también al worker que la sirve.

**Consecuencias:** aislamiento de red/credenciales exigido en despliegue (DevOps/Cloud), endpoint interno de callback en Spring Boot fuera de `/api/v1` público.

**Documentos afectados:** `BRIKA_MASTER_SPEC.md`, `03_TECHNICAL_SPECIFICATION.md`, `21_AI_V1_SCOPE.md`, `06_SECURITY_SPECIFICATION.md`, `23_CLOUD_DEPLOYMENT_SPECIFICATION.md`.

**Estado:** APPROVED.

---

## ADR-RBAC-001 — Role-Permission Assignment Matrix

**Contexto:** Sprint 1 dejó `role_permissions` intencionadamente vacía (ver `V3__seed_roles_permissions.sql`): `14_DEFINITIVE_PERMISSION_CATALOG.md` define 110 permisos atómicos y 4 roles (`SUPERADMIN`, `MANAGER`, `BROKER`, `CLIENT`, `ADR-004`), pero ningún documento del proyecto contenía una asignación rol→permiso explícita a nivel atómico. Sprint 2 (Identity + Tenant + RBAC, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`) necesita esa asignación para ser funcional.

**Problema:**
1. Asignar permisos sin base documental suficiente equivale a inventar una regla de negocio (`CLAUDE.md` §3, §10).
2. `05_PERMISSIONS_MATRIX.md` —la única fuente con forma de matriz— está autodeclarada histórica y describe capacidades por recurso de forma cualitativa, no permisos atómicos.
3. `SUPERADMIN` necesita acceder a recursos tenant-owned para dar soporte, pero ningún permiso puede implicar acceso cross-tenant permanente sin violar el aislamiento de tenant (`CLAUDE.md` §6, `ADR-010`).

**Decisión:** Se aprueba la matriz completa de 440 combinaciones rol-permiso (110 permisos × 4 roles) detallada íntegramente más abajo, construida sobre `BRIKA_MASTER_SPEC.md` §4 (capacidades por rol) combinado con §8 (constituyentes explícitos de `CASE`), `14_DEFINITIVE_PERMISSION_CATALOG.md` §19-§21, las ADR previas de este mismo documento, y decisiones explícitas del promotor del proyecto registradas durante el diseño de este ADR. Se introduce `SUPPORT_SESSION` como único mecanismo de acceso de `SUPERADMIN` a recursos tenant-owned.

Verificación mecánica (no manual) previa a esta decisión: 110 permisos en catálogo, 4 roles, 440 combinaciones, 81/71/58/11 `APPROVED` (SUPERADMIN/MANAGER/BROKER/CLIENT), 4/6/6/0 `PENDING`, 25/33/46/99 `NOT_ASSIGNED`, cero duplicados, cero permisos del catálogo ausentes de la matriz, cero permisos inventados.

### Matriz definitiva — 110 permisos × 4 roles

Formato por celda: `ESTADO` y, cuando aplica, `(SCOPE)`. Scopes usados: `GLOBAL` (catálogo/plataforma, sin tenant), `TENANT` (limitado a la propia empresa), `CASE` (limitado además a `case_assignments`/participación), `PORTAL` (Portal Cliente, sujeto a `document_publications`/`conversation_participants`/visibility), `SUPPORT_SESSION` (solo `SUPERADMIN`, exige sesión de soporte activa — ver sección dedicada).

#### Plataforma

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `COMPANY_CREATE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Ciclo de vida de empresa = acción de plataforma, no dato tenant-owned |
| `COMPANY_READ` | APPROVED (GLOBAL) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `COMPANY_UPDATE` | APPROVED (GLOBAL) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `COMPANY_SUSPEND` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | MANAGER no puede autosuspender su empresa |
| `COMPANY_DELETE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |

#### Planes y suscripciones (`ADR-PLATFORM-001`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `PLAN_READ` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Catálogo §3B: "uso exclusivo SUPERADMIN" |
| `PLAN_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |
| `SUBSCRIPTION_READ` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Decisión explícita: gestión de planes/suscripciones exclusiva de SUPERADMIN |
| `SUBSCRIPTION_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |

#### Usuarios

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `USER_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `USER_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | BROKER: necesidad operativa derivada de `TASK_ASSIGN`/`CASE_ASSIGN` |
| `USER_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `USER_DISABLE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `USER_ASSIGN_ROLE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |

#### Clientes

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `CLIENT_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CLIENT_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CLIENT_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CLIENT_EXPORT` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Acción sensible (exportación de PII), sin base documental suficiente para ningún rol en V1 |
| `CLIENT_DELETE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | No asignado en V1, decisión explícita |

#### Operaciones (`CASE`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `CASE_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | Sin caso previo que scopee la creación |
| `CASE_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Ejemplo canónico del catálogo §21 |
| `CASE_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CASE_ASSIGN` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Acción de supervisión |
| `CASE_CHANGE_STATUS` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CASE_CANCEL` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CASE_REOPEN` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Decisión explícita: exclusivo MANAGER |
| `CASE_EXPORT` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Decisión explícita: exclusivo MANAGER |

#### Inmuebles

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `PROPERTY_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Inferencia derivada: `BRIKA_MASTER_SPEC.md` §8 (PROPERTY es constituyente de CASE) + §4 "operaciones" |
| `PROPERTY_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Inferencia derivada, igual que arriba |
| `PROPERTY_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Inferencia derivada, igual que arriba |
| `PROPERTY_DELETE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |

#### Documentos

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `DOCUMENT_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | CLIENT usa `PORTAL_DOCUMENT_READ` |
| `DOCUMENT_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_REQUEST` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_UPLOAD` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_DOWNLOAD` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_REVIEW` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_APPROVE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_REJECT` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_DELETE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | No asignado en V1, decisión explícita; contradice principio de versionado sin sobrescritura |
| `DOCUMENT_PUBLISH` | NOT_ASSIGNED | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | `BRIKA_MASTER_SPEC.md` §7 nombra explícitamente al broker; sin caso de uso de soporte plausible para SUPERADMIN |
| `DOCUMENT_UNPUBLISH` | NOT_ASSIGNED | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Igual que arriba |
| `DOCUMENT_REQUIREMENT_READ` | APPROVED (GLOBAL) | APPROVED (GLOBAL) | APPROVED (GLOBAL) | NOT_ASSIGNED | Catálogo global (`ADR-DOC-001`), lectura abierta a todo rol interno |
| `DOCUMENT_REQUIREMENT_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Gestión de catálogo global, patrón SUPERADMIN-only |

#### Financiación

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `SIMULATION_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `SIMULATION_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `SIMULATION_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `FINANCING_REQUEST_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `FINANCING_REQUEST_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `FINANCING_REQUEST_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `FINANCING_FINALIZE` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Decisión explícita: acción irreversible, exclusivo MANAGER |

#### Bancos

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `BANK_READ` | APPROVED (GLOBAL) | APPROVED (GLOBAL) | APPROVED (GLOBAL) | NOT_ASSIGNED | `BANK` es catálogo global (`15_DEFINITIVE_ERD.md` §9) |
| `BANK_CREATE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Gestión de catálogo global |
| `BANK_UPDATE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |
| `BANK_CRITERIA_READ` | APPROVED (GLOBAL) | APPROVED (GLOBAL) | APPROVED (GLOBAL) | NOT_ASSIGNED | |
| `BANK_CRITERIA_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |
| `BANK_REQUEST_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `BANK_REQUEST_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `BANK_RESPONSE_REGISTER` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `BANK_OFFER_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `BANK_OFFER_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Visibilidad de CLIENT sobre ofertas publicadas se resuelve vía `PORTAL_CASE_READ` + `document_publications`, nunca este permiso interno |
| `BANK_OFFER_SELECT` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Riesgo medio-alto señalado en revisión: acción de naturaleza similar a `FINANCING_FINALIZE`; se mantiene en BROKER por respaldo explícito de §4.3 "bancos", pero queda anotado como candidato a revisión futura |

#### Contactos bancarios (`ADR-BANK-001`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `BANK_CONTACT_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | Contacto es propiedad de la empresa, no del caso (`ADR-BANK-001`) — scope `TENANT`, no `CASE` |
| `BANK_CONTACT_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | |
| `BANK_CONTACT_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | |
| `BANK_CONTACT_DELETE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | |

#### Tareas

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `TASK_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_ASSIGN` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_COMPLETE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_DELETE` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Riesgo medio señalado en revisión: más irreversible que el resto del grupo; se mantiene en MANAGER por ser rol de supervisión |

#### Comunicación (`ADR-COMMS-001`, `ADR-COMMS-002`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `CONVERSATION_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CONVERSATION_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | CLIENT usa `PORTAL_MESSAGE_READ` + `conversation_participants` |
| `CONVERSATION_PARTICIPANT_MANAGE` | APPROVED (SUPPORT_SESSION) | APPROVED (CASE) | APPROVED (CASE) | NOT_ASSIGNED | Scope `CASE` explícito para MANAGER/BROKER (decisión: "siempre limitado por tenant + acceso al CASE/conversación") |
| `MESSAGE_SEND` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `MESSAGE_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `MESSAGE_ATTACHMENT_UPLOAD` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `MESSAGE_ATTACHMENT_DOWNLOAD` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |

#### Notificaciones

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `NOTIFICATION_READ` | NOT_ASSIGNED | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | `notifications.company_id` es `NOT NULL` (`16_POSTGRESQL_SCHEMA_SPECIFICATION.md` §11) y SUPERADMIN "no pertenece necesariamente a una empresa" (`BRIKA_MASTER_SPEC.md` §4.1) — incompatibilidad estructural, no solo ausencia de evidencia |
| `NOTIFICATION_MANAGE` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Igual restricción estructural para SUPERADMIN |

#### Actividad (`ADR-AUDIT-001`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `ACTIVITY_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Respeta `CASE_READ` del mismo rol (`ADR-AUDIT-001`) |

#### Scoring

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `SCORING_RUN` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Riesgo medio señalado en revisión: grupo con menor anclaje textual directo (solo inferencia derivada §8, ningún rol lo nombra explícitamente) |
| `SCORING_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Igual que arriba |
| `SCORING_RULESET_READ` | APPROVED (GLOBAL) | APPROVED (GLOBAL) | APPROVED (GLOBAL) | NOT_ASSIGNED | Consecuencia de transparencia/explicabilidad (`BRIKA_MASTER_SPEC.md` §12) |
| `SCORING_RULESET_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Catálogo de reglas versionado, gestión SUPERADMIN-only |

#### Auditoría

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `AUDIT_READ` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Necesidad funcional: única forma de auditar el uso de `SUPPORT_SESSION`; decisión explícita: auditoría restringida a SUPERADMIN |
| `AUDIT_EXPORT` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Igual |

#### IA

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `AI_USE` | PENDING | PENDING | PENDING | NOT_ASSIGNED | Falta decisión de producto: qué caso de uso IA (`21_AI_V1_SCOPE.md`) está aprobado por rol. No asignar hasta entonces |
| `AI_DOCUMENT_ANALYZE` | PENDING | PENDING | PENDING | NOT_ASSIGNED | Igual |
| `AI_SUMMARIZE` | PENDING | PENDING | PENDING | NOT_ASSIGNED | Igual |
| `AI_DRAFT_MESSAGE` | PENDING | PENDING | PENDING | NOT_ASSIGNED | Igual |
| `AI_MANAGE_CONFIGURATION` | APPROVED (GLOBAL) | PENDING | PENDING | NOT_ASSIGNED | Configuración de IA es responsabilidad de plataforma (`BRIKA_MASTER_SPEC.md` §13) |
| `AI_READ_USAGE` | APPROVED (GLOBAL) | PENDING | PENDING | NOT_ASSIGNED | Igual |

#### Reporting

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `REPORT_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | `BRIKA_MASTER_SPEC.md` §4.2 "reporting" explícito para MANAGER; no nombrado para BROKER |
| `REPORT_EXPORT` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Acción distinta de "consultar reporting", sensible, sin base suficiente |

#### Integraciones (`ADR-INTEGRATIONS-001`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `INTEGRATION_READ` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Fuente: decisión explícita del promotor del proyecto durante el diseño de este ADR, no derivada de documentación previa |
| `INTEGRATION_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Igual |
| `INTEGRATION_EXECUTE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Catálogo §18: "no se ejercita en V1" |

#### Portal Cliente

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `PORTAL_DASHBOARD_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | Catálogo §19, "Capacidades iniciales" |
| `PORTAL_CASE_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_DOCUMENT_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_DOCUMENT_UPLOAD` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_DOCUMENT_REQUEST_RESPOND` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_MESSAGE_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | Sujeto adicionalmente a `conversation_participants` (`ADR-COMMS-002`) |
| `PORTAL_MESSAGE_SEND` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | Igual |
| `PORTAL_MESSAGE_ATTACHMENT_UPLOAD` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | Igual (`ADR-COMMS-001`) |
| `PORTAL_NOTIFICATION_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_PROFILE_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_PROFILE_UPDATE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |

### Reglas de autorización obligatorias

Ningún permiso implica autorización efectiva por sí solo. Toda autorización se evalúa como:

```
TENANT + ROLE/PERMISSION + RESOURCE SCOPE
```

y, cuando el recurso es un `CASE` o deriva de uno:

```
TENANT + ROLE/PERMISSION + CASE ASSIGNMENT
```

Reglas adicionales no expresables únicamente mediante `role_permissions`:

- **Permission + entitlement** (`ADR-PLATFORM-001`): funcionalidad limitada por plan exige además el `entitlement` activo de la suscripción.
- **Permission + participant + visibility** (`ADR-COMMS-002`): conversaciones tipo `CLIENT` exigen `tenant + case + participant + visibility`.
- **Permission + SUPPORT_SESSION activa:** todo permiso marcado `(SUPPORT_SESSION)` exige, además del rol, una `support_session` `ACTIVE` no caducada para la `company_id` objetivo. Sin sesión activa, acceso `DENIED`, aunque `role_permissions` conceda el permiso.
- **CLIENT** queda además sujeto en todo momento a `document_publications` (visibilidad de documentos), `conversation_participants` (mensajería) y a que la información esté expresamente publicada (`06_SECURITY_SPECIFICATION.md` §8) — nunca accede por el mero hecho de pertenecer al `CASE`.

### Tenant isolation

Ningún rol tiene ningún permiso con acceso cross-tenant permanente. `TenantContext` resuelve `company_id` exclusivamente desde la identidad autenticada, nunca desde un valor enviado por el cliente (`CLAUDE.md` §6, `ADR-010`). Para `SUPERADMIN`, `TenantContext` no resuelve ningún tenant salvo que exista una `SUPPORT_SESSION` `ACTIVE` — en cualquier otro momento, `SUPERADMIN` no tiene tenant resuelto y no puede ejercer ninguno de sus permisos `(SUPPORT_SESSION)`, aunque `role_permissions` se los conceda.

### SUPPORT_SESSION — diseño conceptual

Único mecanismo autorizado para que `SUPERADMIN` acceda a recursos tenant-owned.

Entidad conceptual `support_sessions` (no implementada todavía): `id`, `superadmin_user_id`, `target_company_id` (obligatorio, exactamente una empresa, nunca `'*'`), `reason` (obligatorio), `started_at`, `expires_at` (obligatorio, sin sesiones indefinidas), `ended_at` nullable, `status` (`ACTIVE`/`EXPIRED`/`CLOSED`).

Reglas obligatorias: no cambia el rol de `SUPERADMIN`; no concede permisos nuevos; solo habilita el uso de permisos ya marcados `(SUPPORT_SESSION)` en esta matriz, y solo contra `target_company_id`; toda acción durante la sesión genera `AuditEvent` con `support_session_id`; al expirar o cerrarse, el acceso tenant desaparece inmediatamente, sin periodo de gracia.

**`SUPPORT_SESSION` no se implementa en Sprint 2** — `25_CLAUDE_CODE_EXECUTION_GUIDE.md` no lo contempla explícitamente en el alcance de ningún sprint todavía. Los 57 permisos `(SUPPORT_SESSION)` pueden sembrarse en `role_permissions` durante Sprint 2 (la concesión es segura por sí sola: sin `TenantContext` con verificación de sesión, que tampoco se implementa todavía, no hay ningún endpoint que pueda ejercerlos), pero **ningún endpoint o servicio debe consumirlos** hasta que existan `support_sessions`, la verificación de sesión en `TenantContext`, y la columna `support_session_id` en `audit_events`. Sprint 2 sí debe implementar, desde el primer commit de `TenantContext`, la regla "`SUPERADMIN` sin sesión activa = sin tenant resuelto" como comportamiento por defecto.

### Estados PENDING (16 combinaciones)

`AI_USE`, `AI_DOCUMENT_ANALYZE`, `AI_SUMMARIZE`, `AI_DRAFT_MESSAGE` para `SUPERADMIN`/`MANAGER`/`BROKER` (12 combinaciones) y `AI_MANAGE_CONFIGURATION`/`AI_READ_USAGE` para `MANAGER`/`BROKER` (4 combinaciones). Ningún endpoint de IA puede consumir estos permisos hasta que exista una decisión de producto explícita sobre qué caso de uso de `21_AI_V1_SCOPE.md` está aprobado para qué rol — recomendado antes de Sprint 10 (AI Gateway).

### NOT_ASSIGNED

Detalle completo en la matriz de arriba. Categorías: exclusiones estructurales (`*_DELETE` de clientes/documentos/inmuebles, `INTEGRATION_EXECUTE`), exclusiones por incompatibilidad de esquema (`NOTIFICATION_READ/MANAGE` para SUPERADMIN), exclusiones por decisión explícita (`SUBSCRIPTION_*`/`AUDIT_*` fuera de MANAGER; `CASE_REOPEN`/`CASE_EXPORT`/`FINANCING_FINALIZE` exclusivos de MANAGER), y catálogos globales fuera de SUPERADMIN.

**Alternativas consideradas:** (a) conceder a SUPERADMIN acceso directo permanente a recursos tenant-owned por simplicidad de implementación — rechazada explícitamente, viola `ADR-010`/`CLAUDE.md` §6; (b) no modelar `SUPPORT_SESSION` y dejar todos los permisos tenant-owned de SUPERADMIN en `NOT_ASSIGNED` indefinidamente — rechazada, bloquearía cualquier capacidad de soporte sin fecha; (c) implementar `SUPPORT_SESSION` completo ya en Sprint 2 — rechazada, expande el alcance definido para ese sprint en `25_CLAUDE_CODE_EXECUTION_GUIDE.md` sin necesidad inmediata.

**Consecuencias:** `role_permissions` puede poblarse en Sprint 2 con las 221 combinaciones `APPROVED` (81+71+58+11). `TenantContext` de Sprint 2 debe nacer con la regla de `SUPERADMIN` sin sesión. Ningún endpoint puede consumir permisos `(SUPPORT_SESSION)` ni los 16 `PENDING` de IA hasta que existan sus mecanismos respectivos.

**Documentos afectados:** `06_SECURITY_SPECIFICATION.md` (nueva sección `SUPPORT_SESSION` + reglas de scope), `14_DEFINITIVE_PERMISSION_CATALOG.md` (referencia a este ADR), `25_CLAUDE_CODE_EXECUTION_GUIDE.md` (alcance exacto de Sprint 2 respecto a `role_permissions`/`TenantContext`/`SUPPORT_SESSION`), `08_REQUIREMENTS_TRACEABILITY.md` (nuevo `BRK`).

**Estado:** APPROVED.
