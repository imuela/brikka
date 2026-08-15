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
