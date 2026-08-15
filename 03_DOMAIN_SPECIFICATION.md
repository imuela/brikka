# BRIKA — DOMAIN SPECIFICATION V1

## 1. Bounded contexts funcionales

### Identity & Tenancy
Empresas, usuarios, roles, permisos, sesiones y aislamiento.

### Platform
Planes, entitlements, suscripciones de empresa e integraciones. Ver `ADR-PLATFORM-001` y `ADR-INTEGRATIONS-001`.

### CRM
Clientes, perfiles y relaciones.

### Cases
Operaciones, participantes, asignaciones y estados.

### Property
Inmuebles, valoración y datos relacionados.

### Documents
Tipos, requisitos, solicitudes, documentos, versiones y archivos.

### Financing
Simulaciones, solicitudes, financiación final.

### Banking
Entidades, criterios, solicitudes, respuestas y ofertas.

### Workflow
Estados, transiciones, tareas y automatizaciones.

### Communications
Conversaciones, mensajes y notificaciones.

### Scoring
Reglas, versiones, resultados y explicación.

### Audit
Eventos de seguridad/cumplimiento (`AuditEvent`) y trazabilidad. Distinto del timeline funcional de negocio (`Activity`), que vive en su propio contexto (ver más abajo). No se derivan uno del otro (`ADR-AUDIT-001`).

### Activity Feed
Timeline funcional de negocio, legible por dashboards, con autorización estándar por recurso.

### AI
Gateway, orchestrator, herramientas, RAG y consumo.

## 2. Entidades conceptuales

- Company
- User
- Role
- Permission
- Plan
- Entitlement
- PlanEntitlement
- CompanySubscription
- Integration
- Client
- ClientPortalAccount
- Case
- CaseClient
- CaseAssignment
- Property
- Simulation
- FinancingRequest
- Bank
- BankContact
- BankCriteriaVersion
- BankRequest
- BankResponse
- BankOffer
- FinalFinancing
- DocumentType
- DocumentRequirement
- DocumentRequest
- Document
- DocumentVersion
- DocumentPublication
- Task
- Conversation
- ConversationParticipant
- Message
- MessageAttachment
- Notification
- NotificationDelivery
- ScoringRuleSet
- ScoringRule
- ScoringResult
- AuditEvent
- Activity
- AIUsage

**Nota:** `File` no es una entidad conceptual independiente. Sus atributos (almacenamiento, nombre original, tipo MIME, tamaño, checksum) quedan formalmente absorbidos en `DocumentVersion`, al no existir ningún caso de uso V1 de una versión documental con múltiples ficheros (`ADR-DOC-001`).

## 3. Relaciones clave

Una Company posee usuarios y operaciones.

Un Case pertenece a una Company y puede tener múltiples Client.

Un Client puede participar en múltiples Case.

Un Case puede tener un Property.

Un Case puede tener múltiples Document, Task, FinancingRequest, BankRequest, BankOffer y Conversation.

Los Document tienen versiones.

Los recursos pertenecientes a un tenant deben mantener aislamiento de tenant.

## 4. Datos declarados

Los datos relevantes para análisis hipotecario deben poder conservar:
- valor;
- origen/procedencia;
- fecha de obtención;
- usuario o sistema que los introdujo;
- evidencia asociada cuando corresponda;
- historial de modificaciones.

## 5. Perfil financiero

El perfil financiero del cliente debe tratarse como información de negocio controlada. No se permitirá modificarlo sin las reglas de autorización, trazabilidad y procedencia definidas por el sistema.

## 6. Separación de conceptos

TASK no es DOCUMENT_REQUEST.

DOCUMENT_REQUEST expresa una necesidad documental.

TASK representa trabajo operativo.

SIMULATION no es BANK_OFFER.

BANK_OFFER no es FINAL_FINANCING.

SCORING_RESULT no es una aprobación bancaria.

DOCUMENT_REQUIREMENT no es DOCUMENT_REQUEST.

DOCUMENT_REQUIREMENT es la regla de catálogo (qué documentación hace falta y en qué condiciones). DOCUMENT_REQUEST es la petición concreta a un cliente en un caso concreto. Un DOCUMENT_REQUEST puede originarse en un DOCUMENT_REQUIREMENT o crearse de forma ad-hoc (`ADR-DOC-001`).

ACTIVITY no es AUDIT_EVENT.

AUDIT_EVENT es el registro de seguridad/cumplimiento, inmutable y de acceso restringido. ACTIVITY es el timeline funcional de negocio, de autorización estándar por recurso. Ninguna se deriva de la otra (`ADR-AUDIT-001`).

RBAC PERMISSION no es ENTITLEMENT.

Un PERMISSION expresa qué puede hacer un rol/usuario. Un ENTITLEMENT expresa qué funcionalidad ha contratado la empresa mediante su PLAN. La autorización efectiva de una funcionalidad limitada por plan requiere ambos: `tenant + permission + entitlement` (`ADR-PLATFORM-001`).

## 7. Principio

Las entidades de dominio representan hechos y conceptos de negocio. Los detalles de persistencia se definirán posteriormente sin alterar este modelo conceptual.


## 8. Regla específica de contactos bancarios

`Bank` representa la entidad bancaria global.

`BankContact` representa una relación de contacto mantenida por una empresa de Brika con ese banco.

La empresa es la propietaria del contacto. El broker que lo crea o utiliza no pasa a ser el propietario del dato.

Una empresa puede tener N contactos para el mismo banco.

Los contactos de una empresa son invisibles para otras empresas.
