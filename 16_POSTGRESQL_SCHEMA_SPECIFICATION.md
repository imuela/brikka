# BRIKA — POSTGRESQL SCHEMA SPECIFICATION V1

## 1. Principios

- PostgreSQL como base de datos principal.
- UUID como identificador público de entidades de negocio.
- `created_at` y `updated_at` en entidades mutables.
- `company_id` obligatorio en recursos tenant-owned. Excepción única: `users.company_id` es nullable para permitir `SUPERADMIN` sin empresa (`ADR-IDENTITY-001`); para MANAGER/BROKER/CLIENT sigue siendo obligatorio, aplicado en capa de aplicación.
- FK con restricciones explícitas.
- Índices orientados a tenant + consultas frecuentes.
- JSONB sólo para metadata/configuración realmente variable.
- Flyway será la autoridad de migraciones.
- El backend es la autoridad de autorización; RLS puede reforzar el aislamiento.

## 2. Convenciones

Tablas en `snake_case`, singular conceptual/plural físico consistente. Se usará plural en SQL.

PK: `uuid`.

FK: `<entity>_id`.

Fechas: `timestamptz`.

Dinero: `numeric(14,2)`.

Porcentajes/tipos financieros calculados: `numeric` con precisión suficiente, evitando float.

## 3. Tablas principales

### companies
- id uuid PK
- legal_name
- trade_name
- tax_id
- status
- created_at
- updated_at

### users
- id uuid PK
- company_id FK, **nullable** (`V8`): NULL únicamente para `SUPERADMIN`; obligatorio en aplicación para MANAGER/BROKER/CLIENT (`ADR-IDENTITY-001`)
- external_identity_id
- email
- first_name
- last_name
- status
- created_at
- updated_at

Índices: `uq_users_company_email (company_id, email)` para usuarios con empresa; `uq_users_email_no_company (email) WHERE company_id IS NULL` para SUPERADMIN — necesario porque SQL no detecta duplicados de NULL en un índice único compuesto (`ADR-IDENTITY-001`).

### roles
- id uuid PK
- code UNIQUE
- name

### permissions
- id uuid PK
- code UNIQUE
- name

### user_roles
- user_id FK
- role_id FK
- PK(user_id, role_id)

### role_permissions
- role_id FK
- permission_id FK
- PK(role_id, permission_id)

## 3B. Plataforma — Planes y Suscripciones

Añadido en `ADR-PLATFORM-001`. Gestión exclusiva de SUPERADMIN. Separado explícitamente de `companies.status` (ver §5) y de RBAC: la autorización de una funcionalidad limitada por plan combina `permission` (RBAC) + `entitlement` (plan) + `tenant`.

No incluye facturación ni pago automático (fuera de V1).

### plans
- id uuid PK
- code UNIQUE
- name
- status
- created_at
- updated_at

### entitlements
- id uuid PK
- code UNIQUE
- name
- description
- value_type (BOOLEAN/NUMERIC/JSON)

### plan_entitlements
- plan_id FK
- entitlement_id FK
- value jsonb
- PK(plan_id, entitlement_id)

### company_subscriptions
- id uuid PK
- company_id FK UNIQUE
- plan_id FK
- status
- started_at
- current_period_end nullable
- cancelled_at nullable
- created_at
- updated_at

UNIQUE(company_id): una suscripción activa por empresa en V1.

## 4. CRM

### clients
- id uuid PK
- company_id FK
- first_name
- last_name
- email
- phone
- status
- created_at
- updated_at

### client_portal_accounts
- id uuid PK
- company_id FK
- client_id FK
- external_identity_id
- status
- last_login_at
- created_at
- updated_at

## 5. Cases

### cases
- id uuid PK
- company_id FK
- reference
- status
- operation_type
- created_by
- created_at
- updated_at
- cancelled_at

UNIQUE(company_id, reference).

### case_clients
- case_id FK
- client_id FK
- participation_type
- is_primary
- created_at
- PK(case_id, client_id)

### case_assignments
- id uuid PK
- company_id FK
- case_id FK
- user_id FK
- assignment_type
- active
- created_at
- ended_at

### case_status_history
- id uuid PK
- company_id FK
- case_id FK
- previous_status
- new_status
- changed_by
- changed_at
- reason
- metadata jsonb

## 6. Property

### properties
- id uuid PK
- company_id FK
- case_id FK UNIQUE
- address data
- property_type
- valuation
- purchase_price
- created_at
- updated_at

Los campos de dirección se detallarán en migraciones/DTO según el modelo final.

## 7. Documents

### document_types
- id uuid PK
- code UNIQUE
- name
- active

### document_requirements

Añadida en `ADR-DOC-001`. Catálogo global versionable de reglas de documentación necesaria. `conditions` queda deliberadamente abierto en `jsonb` para poder incorporar en el futuro condiciones por tipo de operación, perfil del cliente, banco, producto u otras, sin migración de esquema.

- id uuid PK
- operation_type
- document_type_id FK
- mandatory boolean
- conditions jsonb
- active
- created_at
- updated_at

### document_requests
- id uuid PK
- company_id FK
- case_id FK
- document_type_id FK
- requirement_id FK nullable
- requested_from_client_id nullable
- status
- due_at
- requested_by
- created_at
- updated_at

`requirement_id` referencia la regla de catálogo que originó la solicitud, cuando exista. Nullable porque un broker puede crear una solicitud ad-hoc no derivada de `document_requirements`.

### documents
- id uuid PK
- company_id FK
- case_id FK
- document_type_id FK
- current_version_id nullable
- status
- created_at
- updated_at

### document_versions
- id uuid PK
- document_id FK
- version_number
- storage_key
- original_filename
- mime_type
- size_bytes
- checksum
- uploaded_by
- uploaded_at
- review_status
- reviewed_by nullable
- reviewed_at nullable

UNIQUE(document_id, version_number).

### document_publications
- id uuid PK
- company_id FK
- document_id FK
- document_version_id FK
- published_to_portal
- published_by
- published_at
- revoked_at nullable

## 8. Financing

### simulations
- id uuid PK
- company_id FK
- case_id FK
- principal
- interest_rate
- term_months
- estimated_payment
- metadata jsonb
- created_by
- created_at

### financing_requests
- id uuid PK
- company_id FK
- case_id FK
- status
- requested_amount
- term_months
- created_at
- updated_at

## 9. Banking

### banks
Catálogo global.
- id uuid PK
- code UNIQUE
- name
- status
- metadata jsonb
- created_at
- updated_at

### bank_products
- id uuid PK
- bank_id FK
- code
- name
- status
- metadata jsonb

### bank_criteria_versions
- id uuid PK
- bank_id FK
- version
- status
- effective_from
- effective_to nullable
- rules jsonb
- created_at

### bank_contacts
Tenant-owned.
- id uuid PK
- company_id FK
- bank_id FK
- owner_user_id nullable
- name
- position
- department
- branch
- email
- phone
- secondary_phone
- notes
- visibility
- active
- created_at
- updated_at

Índice: `(company_id, bank_id, active)`.

### bank_requests
- id uuid PK
- company_id FK
- case_id FK
- bank_id FK
- bank_contact_id nullable
- status
- submitted_at
- created_at
- updated_at
- contact_snapshot jsonb

El `contact_snapshot` permite reconstrucción histórica sin depender de los datos actuales del contacto.

### bank_responses
- id uuid PK
- bank_request_id FK
- status
- received_at
- summary
- payload jsonb
- created_at

### bank_offers
- id uuid PK
- company_id FK
- bank_request_id FK
- bank_id FK
- status
- amount
- interest_rate
- term_months
- payment
- conditions jsonb
- received_at
- created_at
- updated_at

### final_financing
- id uuid PK
- company_id FK
- case_id FK UNIQUE
- bank_offer_id FK
- status
- finalized_at
- created_at
- updated_at

## 10. Tasks

### tasks
- id uuid PK
- company_id FK
- case_id nullable
- assigned_to nullable
- type
- title
- description
- status
- due_at
- created_by
- completed_at
- created_at
- updated_at

## 11. Communications

### conversations
- id uuid PK
- company_id FK
- case_id FK
- type
- status
- created_at
- updated_at

### conversation_participants

Añadida en `ADR-COMMS-002`. Obligatoria a nivel de aplicación para `conversations.type = CLIENT`. Para `type = INTERNAL` en V1 la restricción de acceso sigue siendo implícita vía `case_assignments`, no requiere fila propia.

- id uuid PK
- company_id FK
- conversation_id FK
- participant_user_id nullable
- participant_client_id nullable
- created_at
- removed_at nullable

CHECK: exactamente uno de `participant_user_id` / `participant_client_id` no nulo.

La autorización backend de una conversación tipo `CLIENT` debe evaluar `tenant + case + participant + visibility`. No basta con comprobar que el cliente pertenece a la empresa.

### messages
- id uuid PK
- conversation_id FK
- sender_user_id nullable
- sender_client_id nullable
- body
- created_at
- edited_at nullable

La capa de aplicación validará que exista un único tipo de emisor válido.

### message_attachments

Añadida en `ADR-COMMS-001`. Independiente del pipeline `documents`/`document_versions`; reutiliza las reglas de storage/checksum/MIME/tamaño de `18_STORAGE_SPECIFICATION.md`.

- id uuid PK
- company_id FK
- message_id FK
- storage_key
- original_filename
- mime_type
- size_bytes
- checksum
- created_at

### notifications
- id uuid PK
- company_id FK
- recipient_user_id nullable
- recipient_client_id nullable
- type
- payload jsonb
- read_at nullable
- created_at

`notifications` es agnóstica de canal: representa el evento de negocio, no su entrega.

### notification_deliveries

Añadida en `ADR-NOTIF-001`. Separa la notificación lógica de cada entrega por canal. V1 implementa únicamente `channel IN (IN_APP, EMAIL)`; `PUSH`/`SMS` son valores de catálogo válidos a nivel de schema pero sin worker/proveedor conectado en V1.

- id uuid PK
- notification_id FK
- channel
- status
- provider_reference nullable
- sent_at nullable
- failed_reason nullable
- created_at

## 12. Scoring

### scoring_rulesets
- id uuid PK
- code
- version
- status
- rules jsonb
- created_at

UNIQUE(code, version).

### scoring_rules
- id uuid PK
- ruleset_id FK
- code
- weight
- configuration jsonb

### scoring_results
- id uuid PK
- company_id FK
- case_id FK
- ruleset_id FK
- total_score
- category
- explanation jsonb
- calculated_at

## 13. AI

### document_extractions
- id uuid PK
- company_id FK
- document_version_id FK
- status
- provider
- model
- extracted_data jsonb
- confidence jsonb
- validated_by nullable
- validated_at nullable
- created_at

### ai_usage
- id uuid PK
- company_id FK
- case_id nullable
- user_id nullable
- provider
- model
- operation
- input_tokens nullable
- output_tokens nullable
- estimated_cost nullable
- created_at

## 14. Audit

### audit_events
- id uuid PK
- company_id nullable
- actor_user_id nullable
- actor_client_id nullable
- action
- resource_type
- resource_id
- request_id nullable
- metadata jsonb
- created_at

`audit_events` es el log de seguridad/cumplimiento, inmutable, con acceso restringido por `AUDIT_READ`/`AUDIT_EXPORT`.

## 14B. Actividad funcional

Añadida en `ADR-AUDIT-001`. Distinta de `audit_events`: es el timeline de negocio legible para dashboards ("cliente creado", "documento aprobado"), con autorización estándar por recurso (p. ej. `CASE_READ`), no `AUDIT_READ`. Se alimenta de los mismos eventos de dominio (RabbitMQ) que `audit_events`, mediante un consumidor independiente. Nunca es una vista derivada de `audit_events`.

### activities
- id uuid PK
- company_id FK
- case_id nullable
- client_id nullable
- actor_user_id nullable
- actor_client_id nullable
- activity_type
- summary
- metadata jsonb
- created_at

## 14C. Integraciones

Añadida en `ADR-INTEGRATIONS-001` como estructura mínima de extensibilidad. Ningún proveedor externo concreto se implementa en V1. `integration_events` **no** se crea en V1 salvo dependencia técnica real con ADR propio.

### integrations
- id uuid PK
- company_id nullable
- type
- status
- config jsonb
- credentials_ref nullable
- created_at
- updated_at

`credentials_ref` referencia el secreto en el secret manager; nunca se almacena una credencial en claro (ver `06_SECURITY_SPECIFICATION.md`).

## 15. Tenant isolation

Todas las tablas tenant-owned deben tener `company_id` directamente o derivable mediante una relación segura.

Tenant-owned, incluidas las tablas añadidas por los ADR de la segunda auditoría: `document_requirements` (catálogo global, no tenant-owned), `company_subscriptions`, `activities`, `message_attachments` (derivable vía `conversation_id → conversations.company_id`, se denormaliza `company_id` para simplificar el filtrado), `conversation_participants`, `notification_deliveries` (derivable vía `notification_id`), `integrations` (tenant-owned cuando `company_id` no es null).

Global, no tenant-owned: `document_requirements`, `plans`, `entitlements`, `plan_entitlements`.

El backend debe aplicar filtros de tenant.

RLS se considera una segunda línea de defensa y se activará en tablas donde aporte valor sin introducir complejidad injustificada.

## 16. Migraciones

Flyway:
- V1__initial_schema.sql
- V2__seed_system_catalogs.sql
- V3__seed_roles_permissions.sql
- V4__platform_plans_entitlements.sql (`plans`, `entitlements`, `plan_entitlements`, `company_subscriptions`)
- V5__document_requirements.sql (`document_requirements`, `document_requests.requirement_id`)
- V6__communications_extensions.sql (`conversation_participants`, `message_attachments`, `notification_deliveries`)
- V7__activity_and_integrations.sql (`activities`, `integrations`)
- futuras migraciones incrementales.

Estas migraciones (V4–V7) todavía no se han ejecutado: se crean en Sprint 1, no en Sprint 0 (`ADR-PROCESS-002`).

Nunca modificar una migración ya ejecutada en un entorno compartido.

## 17. Regla

El esquema físico debe derivarse de este documento y del ERD, no de modelos generados automáticamente sin revisión.
