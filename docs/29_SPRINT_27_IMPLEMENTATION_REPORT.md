# 29 — SPRINT 27: INFORME FINAL DE IMPLEMENTACIÓN (CORE FUNCTIONAL V1)

Informe de cierre del Sprint 27, que cubre las **4 carencias funcionales del CRM** autorizadas y
resuelve el "vaciado de funciones" pendiente: (1) SUPERADMIN sin acceso a pantallas, (2) Dashboard
básico, (3) modelo de Cliente insuficiente, (4) modelo de Caso/operación hipotecaria insuficiente.

Cierre: rama `main`, commit `feat(sprint-27): core functional v1` (`14d3ba8`), tag `sprint-27`,
push completado. **No se ha iniciado el Sprint 28.**

---

## 1. Bloques implementados

### Bloque 1 — SUPERADMIN global (ADR-RBAC-002)
SUPERADMIN pasa a ser administrador GLOBAL. Los permisos se siguen comprobando (`requirePermission`);
lo único que cambia es la resolución del tenant: se resuelve desde el **recurso accedido** (no del
principal), mismo patrón que `CompanyController`. Las lecturas son globales en todas las pantallas
tenant (Casos, Clientes, Tareas, Usuarios, Actividad y recursos scoped por caso vía
`CaseAccessService`). `NOTIFICATION_READ` queda fuera (personal, SUPERADMIN no lo tiene → 403).

- Escrituras administrativas globales: Users create con `companyId` obligatorio para SUPERADMIN;
  update/disable resuelven el tenant del usuario objetivo. Companies/Plans/Subscriptions ya eran
  globales.
- Escrituras operativas de tenant (crear caso/cliente/tarea) siguen atadas al tenant del llamante;
  el frontend oculta esos botones para SUPERADMIN (directiva `*appHideForRole`).
- Tests alineados a la nueva expectativa (200): `CrmCaseEndpointsIT`, `FinancingEndpointsIT`,
  `DocumentEndpointsIT`, `IdentityEndpointsIT`; en esta regresión final también se alinearon los 6
  ITs de recursos scoped por caso que aún esperaban 403 (`ScoringEndpointsIT`, `BankMatchingEndpointsIT`,
  `BankMatchOverrideEndpointsIT`, `AiUseCaseEndpointsIT`, `AiDocumentExtractionEndpointsIT`,
  `BankRequestEndpointsIT`) y `FlywayMigrationIT` (contador de migraciones 17→19).

### Bloque 2 — Dashboard básico
`GET /api/v1/dashboard` (requiere `ACTIVITY_READ`). Devuelve `activeCases`, `casesByStatus`
(no terminales), `pendingTasks`, `overdueTasks`, `pendingDocumentRequests`, `recentActivity`
(últimas 10). Alcance: MANAGER = empresa; BROKER = casos asignados (regla CASE ASSIGNMENT);
SUPERADMIN = global. Documentado en `17_API_SPECIFICATION_DETAILED.md` §17D.

### Bloque 3 — Cliente ampliado (migración V18)
`V18__client_extended_attributes.sql` añade columnas nullable: `document_type`, `document_number`,
`date_of_birth`, `nationality`, `address`, `employment_status`. Solo `firstName`/`lastName`/`email`/`phone`
siguen siendo requeridos. `ClientRepository.insert(companyId, firstName, lastName, email, phone)` como
overload de conveniencia. Frontend: `client-form` (campos opcionales, `dateOfBirth` como date) y
`client-detail` (DatePipe).

### Bloque 4 — Caso ampliado (migración V19)
`V19__case_operation_details.sql` añade `requested_amount numeric(14,2)` y `description text`
(nullable) como información inicial de la operación. `CreateCaseApiRequest`/`UpdateCaseApiRequest`
ganan los campos más overloads de 1 arg (para no tocar los call sites de test). `CaseService` expone
`createCase(tenant, createdBy, type, amount, description)` (más overloads) y `updateDetails` para el
PATCH. Frontend: `case-form` (inputs de importe y descripción), `case-detail` (muestra importe y
descripción). Test de round-trip create/get/PATCH añadido en `CrmCaseEndpointsIT`.

## 2. Archivos modificados/creados (resumen)
- Backend: `dashboard/` (repo + controller + response), `casemgmt` (Case, CaseRepository, CaseService,
  CaseController, CaseResponse, Create/UpdateCaseApiRequest), `crm` (Client, ClientRepository,
  ClientController, ClientResponse, Create/UpdateClientApiRequest), `identity` (UserController,
  CreateUserApiRequest, UserRepository), `task/TaskController` + `TaskRepository`,
  `activity/ActivityController` + `ActivityRepository`, `security/AuthorizationService`,
  migraciones V18 y V19.
- Frontend: dashboard (component/service/model/spec), directiva `hide-for-role`, forms/details de
  casos y clientes, botones "Nuevo" con `*appHideForRole`.
- Docs: `12_DECISION_LOG.md` (ADR-RBAC-002), `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`,
  `17_API_SPECIFICATION_DETAILED.md` (§§5/6/7/17D).

## 3. Tests ejecutados
- Backend: `./mvnw verify` → **339 tests, 0 fallos, BUILD SUCCESS**; `./mvnw spotless:check` OK.
- Frontend: `npx ng test --watch=false` → **92 ficheros / 424 tests OK**; `npx ng lint` OK.

## 4. Próximos pasos
Regla de cierre aplicada: implementación técnica completa → tests en verde → documentación
actualizada → commit/tag/push → **detenerse aquí** y esperar instrucciones. **No iniciar Sprint 28.**