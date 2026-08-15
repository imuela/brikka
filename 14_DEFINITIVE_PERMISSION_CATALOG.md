# BRIKA — DEFINITIVE PERMISSION CATALOG V1

## 1. Principio

Los permisos son capacidades atómicas. Los roles agrupan permisos.

La autorización efectiva depende de:
- identidad;
- rol;
- tenant;
- recurso;
- relación con el recurso;
- visibilidad.

## 2. Naming

Formato:

`<RESOURCE>_<ACTION>`

Acciones especiales pueden utilizar verbos específicos.

## 3. Plataforma

- COMPANY_CREATE
- COMPANY_READ
- COMPANY_UPDATE
- COMPANY_SUSPEND
- COMPANY_DELETE

## 3B. Planes y suscripciones

Añadido en `ADR-PLATFORM-001`. Uso exclusivo SUPERADMIN.

- PLAN_READ
- PLAN_MANAGE
- SUBSCRIPTION_READ
- SUBSCRIPTION_MANAGE

Estos permisos son independientes de los `entitlements` de la suscripción: `PLAN_MANAGE` permite administrar planes, no otorga por sí mismo ningún entitlement.

## 4. Usuarios

- USER_CREATE
- USER_READ
- USER_UPDATE
- USER_DISABLE
- USER_ASSIGN_ROLE

## 5. Clientes

- CLIENT_CREATE
- CLIENT_READ
- CLIENT_UPDATE
- CLIENT_EXPORT
- CLIENT_DELETE

## 6. Operaciones

- CASE_CREATE
- CASE_READ
- CASE_UPDATE
- CASE_ASSIGN
- CASE_CHANGE_STATUS
- CASE_CANCEL
- CASE_REOPEN
- CASE_EXPORT

## 7. Inmuebles

- PROPERTY_CREATE
- PROPERTY_READ
- PROPERTY_UPDATE
- PROPERTY_DELETE

## 8. Documentos

- DOCUMENT_READ
- DOCUMENT_CREATE
- DOCUMENT_REQUEST
- DOCUMENT_UPLOAD
- DOCUMENT_DOWNLOAD
- DOCUMENT_REVIEW
- DOCUMENT_APPROVE
- DOCUMENT_REJECT
- DOCUMENT_DELETE
- DOCUMENT_PUBLISH
- DOCUMENT_UNPUBLISH
- DOCUMENT_REQUIREMENT_READ
- DOCUMENT_REQUIREMENT_MANAGE

`DOCUMENT_REQUIREMENT_*` añadidos en `ADR-DOC-001`; gestionan el catálogo de reglas, no las solicitudes concretas (que usan `DOCUMENT_REQUEST`).

## 9. Financiación

- SIMULATION_CREATE
- SIMULATION_READ
- SIMULATION_UPDATE
- FINANCING_REQUEST_CREATE
- FINANCING_REQUEST_READ
- FINANCING_REQUEST_UPDATE
- FINANCING_FINALIZE

## 10. Bancos

- BANK_READ
- BANK_CREATE
- BANK_UPDATE
- BANK_CRITERIA_READ
- BANK_CRITERIA_MANAGE
- BANK_REQUEST_CREATE
- BANK_REQUEST_READ
- BANK_RESPONSE_REGISTER
- BANK_OFFER_CREATE
- BANK_OFFER_READ
- BANK_OFFER_SELECT

## 10B. Contactos bancarios

- BANK_CONTACT_CREATE
- BANK_CONTACT_READ
- BANK_CONTACT_UPDATE
- BANK_CONTACT_DELETE

El alcance se limita a los contactos de la propia empresa y a la visibilidad autorizada.

## 11. Tareas

- TASK_CREATE
- TASK_READ
- TASK_UPDATE
- TASK_ASSIGN
- TASK_COMPLETE
- TASK_DELETE

## 12. Comunicación

- CONVERSATION_CREATE
- CONVERSATION_READ
- CONVERSATION_PARTICIPANT_MANAGE
- MESSAGE_SEND
- MESSAGE_READ
- MESSAGE_ATTACHMENT_UPLOAD
- MESSAGE_ATTACHMENT_DOWNLOAD

`CONVERSATION_PARTICIPANT_MANAGE` añadido en `ADR-COMMS-002` (añadir/quitar participantes). El acceso de lectura/descarga de adjuntos sigue, además del permiso, la comprobación `tenant + case + participant + visibility` cuando la conversación es tipo CLIENT.

## 13. Notificaciones

- NOTIFICATION_READ
- NOTIFICATION_MANAGE

`notification_deliveries` no tiene permiso propio: se lee siempre en el contexto de su `NOTIFICATION_READ` (`ADR-NOTIF-001`).

## 13B. Actividad

- ACTIVITY_READ

Añadido en `ADR-AUDIT-001`. Distinto de `AUDIT_READ`: da acceso al timeline funcional de negocio, no al log de seguridad/cumplimiento. El acceso a la actividad de un `CASE` concreto también respeta `CASE_READ`.

## 14. Scoring

- SCORING_RUN
- SCORING_READ
- SCORING_RULESET_READ
- SCORING_RULESET_MANAGE

## 15. Auditoría

- AUDIT_READ
- AUDIT_EXPORT

## 16. IA

- AI_USE
- AI_DOCUMENT_ANALYZE
- AI_SUMMARIZE
- AI_DRAFT_MESSAGE
- AI_MANAGE_CONFIGURATION
- AI_READ_USAGE

## 17. Reporting

- REPORT_READ
- REPORT_EXPORT

## 18. Integraciones

- INTEGRATION_READ
- INTEGRATION_MANAGE
- INTEGRATION_EXECUTE

`ADR-INTEGRATIONS-001`: en V1 solo se usan `INTEGRATION_READ`/`INTEGRATION_MANAGE` sobre el catálogo mínimo `integrations`. `INTEGRATION_EXECUTE` queda reservado para cuando exista un adapter concreto aprobado; no se ejercita en V1.

## 19. Portal Cliente

Los permisos del rol CLIENT son específicos y no deben reutilizar automáticamente permisos internos.

Capacidades iniciales:
- PORTAL_DASHBOARD_READ
- PORTAL_CASE_READ
- PORTAL_DOCUMENT_READ
- PORTAL_DOCUMENT_UPLOAD
- PORTAL_DOCUMENT_REQUEST_RESPOND
- PORTAL_MESSAGE_READ
- PORTAL_MESSAGE_SEND
- PORTAL_MESSAGE_ATTACHMENT_UPLOAD
- PORTAL_NOTIFICATION_READ
- PORTAL_PROFILE_READ
- PORTAL_PROFILE_UPDATE

`PORTAL_MESSAGE_ATTACHMENT_UPLOAD` cubre la subida de adjuntos en conversaciones del Portal Cliente (`message_attachments`, no `DOCUMENT`). La lectura/descarga de adjuntos se cubre con `PORTAL_MESSAGE_READ`, siempre evaluando además `tenant + case + participant + visibility`.

## 20. Roles base

### SUPERADMIN

Acceso de plataforma y administración global.

### MANAGER

Administración operativa de su empresa según permisos.

### BROKER

Operativa hipotecaria asignada/autorizada.

### CLIENT

Únicamente capacidades del Portal Cliente.

## 21. Scope

Un permiso nunca significa acceso universal.

Ejemplo:

`CASE_READ` + broker autorizado para CASE X = puede leer X.

No significa que pueda leer cualquier CASE del tenant.

## 22. Regla crítica

Toda autorización debe evaluarse en backend.
