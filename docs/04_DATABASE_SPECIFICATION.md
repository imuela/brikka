# BRIKA — DATABASE SPECIFICATION V1

> **Nota:** este documento es el modelo conceptual **histórico**, precursor de `16_POSTGRESQL_SCHEMA_SPECIFICATION.md` (esquema físico definitivo y congelado). Varias entidades listadas aquí (`plans`, `entitlements`, `activities`, `notification_deliveries`, `conversation_participants`, `message_attachments`, `integrations`) fueron reinstauradas en el esquema definitivo mediante los ADR de la segunda auditoría (`12_DECISION_LOG.md`). Ante cualquier discrepancia, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md` prevalece.

## 1. Motor

PostgreSQL.

IDs: UUID.

Fechas: TIMESTAMPTZ.

Importes: NUMERIC.

Flexibilidad controlada: JSONB.

Migraciones: Flyway.

## 2. Entidades principales

### Plataforma/SaaS
- companies
- plans
- entitlements
- plan_entitlements
- company_subscriptions
- integrations
- integration_events

### Identidad
- users
- roles
- permissions
- user_roles
- role_permissions
- user_preferences

### Clientes
- clients
- client_portal_accounts
- client_preferences

### Operaciones
- cases
- case_clients
- case_assignments
- case_status_history

### Inmueble
- properties
- property_valuations

### Financiación
- financing_requests
- simulations
- banks
- bank_requests
- bank_offers
- final_financing

### Documentación
- document_types
- document_requirements
- document_requests
- documents
- document_versions
- files
- message_attachments

### Comunicación
- conversations
- conversation_participants
- messages

### Operativa
- tasks
- notifications
- notification_deliveries
- activities

### Scoring
- scoring_rule_sets
- scoring_rules
- scoring_results

### Auditoría
- audit_logs

### IA
- ai_providers
- ai_usage

## 3. Reglas

Toda entidad de negocio perteneciente a una empresa debe quedar vinculada al tenant de forma directa o mediante una relación segura con su entidad padre.

Las relaciones entre recursos de distintos tenants deben ser imposibles.

## 4. Documentos

DOCUMENT TYPE → REQUIREMENT → REQUEST → DOCUMENT → VERSION → FILE.

Las versiones anteriores se conservan conforme a la política de retención.

## 5. Índices

Se crearán índices para:
- company_id;
- estados;
- asignaciones;
- fechas;
- relaciones frecuentes;
- búsquedas funcionales.

## 6. Integridad

Se utilizarán:
- PK;
- FK;
- UNIQUE;
- CHECK;
- NOT NULL;
- índices;
- constraints de tenant cuando proceda.

## 7. Migraciones

Flyway con migraciones ordenadas:

V001__...
V002__...
etc.

Nunca se editará una migración ya aplicada en un entorno compartido; se añadirá una nueva migración.
