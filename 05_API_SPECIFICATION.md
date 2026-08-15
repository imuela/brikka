# BRIKA — API SPECIFICATION V1

## 1. Principios

API REST versionada.

Base conceptual:
`/api/v1`

JSON como formato principal.

Errores con estructura consistente.

## 2. Recursos principales

- /auth
- /companies
- /users
- /roles
- /permissions
- /plans
- /companies/{id}/subscription
- /clients
- /cases
- /properties
- /financing
- /banks
- /documents
- /document-requirements
- /tasks
- /conversations
- /messages
- /notifications
- /activities
- /scoring
- /reports
- /integrations
- /ai

`/plans` y `/companies/{id}/subscription` son de uso exclusivo SUPERADMIN (`ADR-PLATFORM-001`). `/integrations` en V1 es de solo lectura/estado, sin ejecución de adapters concretos (`ADR-INTEGRATIONS-001`).

## 3. Seguridad

Cada endpoint comprobará:
1. autenticación;
2. permisos (RBAC);
3. entitlement del plan de la empresa, cuando la funcionalidad esté limitada por plan (`ADR-PLATFORM-001`);
4. tenant;
5. ownership/access al recurso (incluye `participant` en conversaciones tipo CLIENT — `ADR-COMMS-002`);
6. visibilidad cuando sea Portal Cliente.

## 4. Convenciones

GET consulta.
POST creación/acción.
PUT/PATCH modificación.
DELETE eliminación cuando esté permitido.

Las operaciones críticas podrán utilizar idempotency keys.

## 5. Errores

Formato conceptual:

{
  "code": "CASE_NOT_FOUND",
  "message": "Operation not found",
  "requestId": "..."
}

No se expondrán stack traces ni información sensible.

## 6. Paginación

Las colecciones utilizarán paginación.

## 7. Auditoría

Los endpoints sensibles generarán eventos/audit logs.

## 8. Portal Cliente

El Portal Cliente tendrá endpoints y políticas de autorización específicas.

No reutilizará ciegamente endpoints internos del broker.
