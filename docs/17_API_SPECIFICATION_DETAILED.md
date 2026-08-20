# BRIKA — API SPECIFICATION DETAILED V1

## 1. Base

`/api/v1`

JSON.

Autenticación mediante Bearer token.

El backend determina tenant y permisos.

## 2. Error estándar

```json
{
  "code": "CASE_NOT_FOUND",
  "message": "Operation not found",
  "requestId": "..."
}
```

No devolver stack traces.

## 3. Pagination

Parámetros:
- page
- size
- sort

Response:
- items
- page
- size
- totalElements
- totalPages

## 4. Auth / session

- GET /api/v1/me
- GET /api/v1/me/permissions

`GET /api/v1/me` incluye también los `entitlements` vigentes de la suscripción de la empresa, para que el frontend pueda ocultar funcionalidades no contratadas (sin que esto sustituya la comprobación de autorización en backend — `ADR-PLATFORM-001`).

## 4A. Companies

Contrato detallado ausente hasta Sprint 12.1 (`ADR-PLATFORM-002`), diseñado por analogía con `/users`: PATCH para datos, endpoint dedicado por cada transición de estado.

- GET /companies (`COMPANY_READ`) — SUPERADMIN: todas las empresas. MANAGER: únicamente la propia (alcance TENANT).
- POST /companies (`COMPANY_CREATE`) — SUPERADMIN exclusivo.
- GET /companies/{id} (`COMPANY_READ`) — MANAGER solo si `id` es su propia empresa; en otro caso, 404 (enmascarado, igual que cualquier lookup cross-tenant).
- PATCH /companies/{id} (`COMPANY_UPDATE`) — `legalName`/`tradeName`/`taxId`. Nunca acepta `status`.
- POST /companies/{id}/suspend (`COMPANY_SUSPEND`) — SUPERADMIN exclusivo. `ACTIVE → SUSPENDED`. Sin endpoint de reactivación (ningún permiso lo habilita).
- DELETE /companies/{id} (`COMPANY_DELETE`) — SUPERADMIN exclusivo. Transición lógica a `DELETED` (`ADR-PLATFORM-002` D-MASTER-2) — nunca un borrado físico de la fila.

## 4B. Plans / Subscriptions

Uso exclusivo SUPERADMIN (`ADR-PLATFORM-001`).

- GET /plans
- POST /plans
- GET /plans/{id}
- PATCH /plans/{id}
- GET /companies/{id}/subscription
- PUT /companies/{id}/subscription — upsert: crea la suscripción si la empresa no tiene una, o actualiza plan/estado si ya existe (`company_subscriptions.company_id` es `UNIQUE`).
- POST /companies/{id}/subscription/cancel

Sin endpoints de facturación/pago: fuera de V1.

## 5. Users

- GET /users
- POST /users
- GET /users/{id}
- PATCH /users/{id}
- POST /users/{id}/disable

## 6. Clients

- GET /clients
- POST /clients
- GET /clients/{id}
- PATCH /clients/{id}

## 7. Cases

- GET /cases
- POST /cases
- GET /cases/{id}
- PATCH /cases/{id}
- POST /cases/{id}/assignments
- GET /cases/{id}/assignments
- POST /cases/{id}/status
- POST /cases/{id}/cancel
- POST /cases/{id}/reopen

## 8. Property

- GET /cases/{caseId}/property
- PUT /cases/{caseId}/property

## 9. Documents

- GET /cases/{caseId}/documents
- POST /cases/{caseId}/documents
- GET /documents/{id}
- GET /documents/{id}/versions
- POST /documents/{id}/versions
- POST /documents/{id}/review
- POST /documents/{id}/publish
- POST /documents/{id}/unpublish

Document upload deberá usar flujo seguro de storage, preferentemente URL temporal/presigned cuando proceda.

## 9B. Document requirements

Catálogo de reglas de documentación necesaria (`ADR-DOC-001`). Gestión típicamente MANAGER/SUPERADMIN.

- GET /document-requirements
- POST /document-requirements
- GET /document-requirements/{id}
- PATCH /document-requirements/{id}

Filtro recomendado: `GET /document-requirements?operationType=...`

## 10. Document requests

- GET /cases/{caseId}/document-requests
- POST /cases/{caseId}/document-requests
- PATCH /document-requests/{id}

`POST /cases/{caseId}/document-requests` acepta `requirementId` opcional cuando la solicitud se origina en un `document_requirements`.

## 11. Simulations

- GET /cases/{caseId}/simulations
- POST /cases/{caseId}/simulations

## 12. Banks

- GET /banks
- GET /banks/{id}
- GET /banks/{id}/products
- GET /banks/{id}/criteria

Catálogo global (`ADR-BANKENGINE-001`), lectura abierta a todo rol interno (`BANK_READ`). Además existen endpoints de escritura, uso exclusivo SUPERADMIN, ausentes de esta sección hasta la revisión de Sprint 12.1 pese a estar ya implementados y protegidos por RBAC desde Sprint 5/6B:

- POST /banks (`BANK_CREATE`)
- PATCH /banks/{id} (`BANK_UPDATE`)
- POST /banks/{id}/products (`BANK_UPDATE`)
- PATCH /bank-products/{id} (`BANK_UPDATE`)
- POST /banks/{id}/criteria (`BANK_CRITERIA_MANAGE`) — crea una nueva `BankCriteriaVersion`; las reglas se validan íntegramente en escritura (`ADR-BANKENGINE-001 D-G`)
- PATCH /bank-criteria-versions/{id} (`BANK_CRITERIA_MANAGE`)

## 13. Bank contacts

- GET /bank-contacts
- POST /bank-contacts
- GET /bank-contacts/{id}
- PATCH /bank-contacts/{id}
- DELETE /bank-contacts/{id}

Filtro recomendado:
`GET /bank-contacts?bankId=...`

El backend debe combinar siempre el filtro con el tenant autorizado.

Nunca confiar en `companyId` recibido desde el cliente.

## 14. Bank requests

- GET /cases/{caseId}/bank-requests
- POST /cases/{caseId}/bank-requests
- GET /bank-requests/{id}
- POST /bank-requests/{id}/responses
- POST /bank-requests/{id}/offers

## 15. Offers

- GET /cases/{caseId}/offers
- GET /bank-offers/{id}
- POST /bank-offers/{id}/select

## 16. Scoring

- POST /cases/{caseId}/scoring/run
- GET /cases/{caseId}/scoring/results

## 17. Tasks

- GET /tasks
- POST /tasks
- PATCH /tasks/{id}
- POST /tasks/{id}/complete

## 17B. Activities

Timeline funcional de negocio, distinto del audit log (`ADR-AUDIT-001`). Autorización estándar por recurso (p. ej. `CASE_READ`), no `AUDIT_READ`.

- GET /cases/{caseId}/activities
- GET /activities (dashboard, filtrado por tenant + asignación)

## 17C. Notifications

- GET /notifications
- GET /notifications/unread-count (Sprint 25: contador de no leídas del badge, scoped al usuario)
- GET /notifications/{id}/deliveries
- PATCH /notifications/{id}/read

`notification_deliveries` es de solo lectura vía API en V1 (se escribe desde los workers de canal, no desde el cliente).

### 17C.1. Eventos conectados (Sprint 25)

Sprint 25 conectó NotificationService a eventos reales de dominio (misma transacción que la acción; si la operación falla no queda notificación). La resolución de destinatarios usa relaciones reales — nunca se notifica al actor, nunca se notifica a otra empresa, y una acción produce exactamente una notificación por destinatario.

| Evento | type | Destinatarios |
| --- | --- | --- |
| Cambio de estado de caso (`changeStatus`) | `CASE_STATUS_CHANGED` | Usuarios asignados al caso salvo el actor |
| Cancelación de caso (`cancel`) | `CASE_CANCELLED` | Usuarios asignados al caso salvo el actor |
| Reapertura de caso (`reopen`) | `CASE_REOPENED` | Usuarios asignados al caso salvo el actor |
| Documento subido por usuario | `DOCUMENT_UPLOADED` | Usuarios asignados al caso salvo el subidor |
| Documento subido por cliente (Portal) | `DOCUMENT_UPLOADED` | Usuarios asignados al caso |
| Documento revisado (aprobado/rechazado) | `DOCUMENT_REVIEWED` | Quien subió la versión revisada (usuario o cliente Portal) |
| Documento publicado en Portal | `DOCUMENT_PUBLISHED` | Clientes del caso (`recipient_client_id`) |
| Mensaje nuevo (usuario) | `NEW_MESSAGE` | Usuarios asignados del caso salvo el autor; en conversación CLIENT también sus participantes cliente (Portal) |
| Mensaje nuevo (cliente Portal) | `NEW_MESSAGE` | Usuarios asignados del caso |

## 17D. Integrations

Estructura mínima de extensibilidad (`ADR-INTEGRATIONS-001`). Solo lectura/estado en V1, sin ejecución de adapters concretos.

- GET /integrations
- GET /integrations/{id}

## 18. Communications

- GET /cases/{caseId}/conversations
- POST /cases/{caseId}/conversations
- GET /conversations/{id}/participants
- POST /conversations/{id}/participants
- DELETE /conversations/{id}/participants/{participantId}
- GET /conversations/{id}/messages
- POST /conversations/{id}/messages
- POST /messages/{id}/attachments
- GET /messages/{id}/attachments

`POST /cases/{caseId}/conversations` de tipo `CLIENT` exige indicar los participantes (`ClientPortalAccount`) en la creación — no puede quedar vacío (`ADR-COMMS-002`). La autorización de lectura/escritura de una conversación tipo `CLIENT` evalúa `tenant + case + participant + visibility`.

## 19. Portal Client

API separada por permisos, no necesariamente por dominio físico:

- GET /portal/me
- GET /portal/cases
- GET /portal/cases/{id}
- GET /portal/cases/{id}/documents
- POST /portal/cases/{id}/documents
- GET /portal/cases/{id}/messages
- POST /portal/cases/{id}/messages
- POST /portal/messages/{messageId}/attachments
- GET /portal/messages/{messageId}/attachments
- GET /portal/notifications
- GET /portal/notifications/unread-count (Sprint 25: contador de no leídas del badge Portal)
- PATCH /portal/notifications/{id}/read (Sprint 19, ADR-PROCESS-007)
- PATCH /portal/profile

`POST/GET /portal/messages/{messageId}/attachments` reutiliza `message_attachments` (`ADR-COMMS-001`); no crea un `DOCUMENT` del pipeline formal. Cada llamada evalúa `tenant + case + participant + visibility` antes del permiso, y aplica las mismas reglas de storage (MIME/tamaño/checksum/descarga mediada por backend) que el resto de ficheros del sistema.

Sólo datos publicados/autorizados.

## 20. Idempotencia

Operaciones externas o sensibles podrán exigir `Idempotency-Key`.

Especialmente:
- envío a banco;
- acciones de pago/integración futura;
- operaciones asíncronas sensibles.

## 21. Autorización

Cada endpoint:
1. autentica;
2. obtiene tenant;
3. verifica permiso (RBAC);
4. verifica entitlement del plan cuando la funcionalidad esté limitada por plan (`ADR-PLATFORM-001`);
5. verifica acceso al recurso (incluye `participant` en conversaciones tipo CLIENT — `ADR-COMMS-002`);
6. ejecuta;
7. audita/registra actividad cuando corresponda (`audit_events` y/o `activities`, según el caso — `ADR-AUDIT-001`).

## 22. OpenAPI

La implementación deberá publicar un contrato OpenAPI versionado.

El contrato OpenAPI se convertirá en fuente de verdad para DTOs y clientes cuando sea viable.
