# BRIKA — SECURITY SPECIFICATION V1

## 1. Principios

Security by design, least privilege, defense in depth y tenant isolation.

## 2. Identidad

Separación conceptual entre:
- usuarios internos;
- clientes del Portal.

## 3. Autorización

RBAC + autorización contextual sobre recursos.

Roles iniciales:
- SUPERADMIN
- MANAGER
- BROKER
- CLIENT

### 3.0 Regla general de autorización efectiva (`ADR-RBAC-001`)

Ningún permiso implica autorización efectiva por sí solo. Toda autorización se evalúa como:

`tenant + role/permission + resource scope`

y, cuando el recurso es un `CASE` o deriva de uno:

`tenant + role/permission + case assignment`

La matriz definitiva de 110 permisos × 4 roles (`APPROVED`/`PENDING`/`NOT_ASSIGNED`, con su `scope`) queda registrada en `12_DECISION_LOG.md` → `ADR-RBAC-001`. Ningún servicio debe consumir un permiso marcado `PENDING` en esa matriz.

### 3.1 RBAC permission vs entitlement de plan

Un `RBAC permission` (qué puede hacer un rol) y un `entitlement` (qué funcionalidad ha contratado la empresa mediante su `plan`) son conceptos distintos y se comprueban por separado (`ADR-PLATFORM-001`).

Una funcionalidad limitada por plan requiere ambas comprobaciones: `tenant + permission + entitlement`. Tener el permiso RBAC nunca es suficiente por sí solo cuando la funcionalidad depende de plan.

### 3.1B SUPERADMIN, tenant resolution y SUPPORT_SESSION (`ADR-RBAC-001` + `ADR-RBAC-002`, Sprint 28)

El diseño original de esta sección (Sprint 2, `ADR-RBAC-001`) preveía `SUPPORT_SESSION` como el
**único** camino de acceso de `SUPERADMIN` a cualquier recurso tenant-owned, tanto para lectura como
para escritura. Ese mecanismo (entidad `support_sessions`, verificación en `TenantContext`,
endpoints de apertura/cierre, `support_session_id` en `audit_events`) **sigue sin implementarse** —
no existe ninguna migración ni tabla `support_sessions` en el esquema. Lo que sí cambió es el modelo
real de acceso de SUPERADMIN, vía Sprint 27 (`ADR-RBAC-002`):

**Lecturas y el subconjunto de escrituras que Sprint 27 cubrió — sin SUPPORT_SESSION:**
`SUPERADMIN` es administrador **GLOBAL**. En vez de resolver su propio tenant (que no tiene —
`TenantContext.resolve` devuelve `Optional.empty()` para este rol, sin excepción), el tenant se
resuelve **desde el recurso accedido** (mismo patrón ya usado por `CompanyController` desde antes de
Sprint 27). Esto cubre:
- lecturas de Casos, Clientes, Tareas, Usuarios y Actividad, y de los recursos derivados de un caso
  (documentos, conversaciones, simulaciones, financiación, ofertas, bank matching) vía
  `CaseAccessService`;
- gestión de usuarios completa (`USER_CREATE`/`READ`/`UPDATE`/`DISABLE`/`ASSIGN_ROLE`): a diferencia
  de las lecturas anteriores, esto **no es un derivado de "resolver desde el recurso"** — es una
  reasignación explícita de esos 5 permisos a alcance GLOBAL, divergiendo del scope `SUPPORT_SESSION`
  que `ADR-RBAC-001` les asignaba originalmente. `USER_CREATE` para SUPERADMIN exige `companyId`
  explícito en la petición (el admin global no tiene empresa propia); MANAGER/BROKER nunca pueden
  influir el tenant de un usuario creado — cualquier `companyId` que envíen se ignora
  (verificado por test, `IdentityEndpointsIT.managerCreatingUserIgnoresAnySuppliedCompanyIdAndUsesOwnTenant`).
- override manual de bank matching (`BANK_MATCHING_OVERRIDE`), vía el mismo `CaseAccessService`.

**Escrituras operativas de tenant que Sprint 27 dejó sin resolver — bloqueadas, sin error 403 no
justificado:** crear/editar Caso, Cliente o Tarea desde SUPERADMIN sigue exigiendo `requireTenant()`
(que SUPERADMIN nunca resuelve). El frontend oculta esos botones de creación para SUPERADMIN
(`*appHideForRole="'SUPERADMIN'"`) en vez de exponerlos para que el backend los rechace. **Esto es lo
único que `SUPPORT_SESSION` seguiría resolviendo si se implementara.**

**Caso aparte, no relacionado con Sprint 27:** `CLIENT_PORTAL_ACCOUNT_CREATE` nunca fue concedido a
SUPERADMIN en absoluto (`V11__portal_account_permission.sql`, Sprint 19, `ADR-PORTAL-AUTH-001`) — es
una exclusión de permiso intencionada y anterior al propio `ADR-RBAC-001`, no un caso pendiente de
SUPPORT_SESSION. Verificado por test (`CrmCaseEndpointsIT.superadminCannotCreateClientPortalAccount`).

**Estado de SUPPORT_SESSION (Sprint 28):** sigue sin implementarse. Su alcance real, si se
implementa, se reduce a las escrituras operativas de tenant listadas arriba — no a "todo lo
tenant-owned" como preveía el diseño original, porque Sprint 27 ya resolvió la mayor parte de las
lecturas y toda la gestión de usuarios por una vía distinta y ya vigente. Mientras no exista,
`TenantContext` sigue resolviendo "sin tenant" para SUPERADMIN sin excepción para su **propio**
tenant — eso no cambia; lo que cambió es que muchos controllers ya no dependen de esa resolución
para SUPERADMIN, consultando en su lugar el tenant del recurso.

### 3.2 Conversaciones (mensajería)

Para conversaciones de tipo `CLIENT`, la autorización backend debe comprobar como mínimo (`ADR-COMMS-002`):

`tenant + case + participant + visibility`

No basta con comprobar que el cliente pertenece a la empresa, ni que el caso pertenece al tenant: debe verificarse explícitamente que el cliente es `CONVERSATION_PARTICIPANT` de esa conversación concreta. El frontend nunca se considera frontera de seguridad para esta comprobación.

Para conversaciones de tipo `INTERNAL`, la autorización se apoya en `CASE_ASSIGNMENT` del usuario sobre el `CASE`.

### 3.3 Adjuntos en conversaciones del Portal Cliente

Las conversaciones del Portal Cliente admiten adjuntos en V1 (`message_attachments`, sin convertirse en `DOCUMENT` formal). Cada acceso o subida exige comprobar, en este orden: tenant → caso → `conversation_participant` → visibility → permiso (`PORTAL_MESSAGE_ATTACHMENT_UPLOAD` / `PORTAL_MESSAGE_READ`). Además, igual que el resto de ficheros del sistema (§10): validación MIME/extensión, límite de tamaño, checksum, almacenamiento privado y descarga siempre mediada por el backend — nunca se expone una URL directa del storage sin autorización previa.

## 4. Tenant isolation

Un tenant no podrá consultar, modificar, descargar ni inferir datos de otro tenant.

Se aplicarán controles en:
- autenticación;
- servicios;
- repositorios/queries;
- storage;
- endpoints;
- Portal Cliente.

## 5. Documentos

El acceso a un archivo requiere verificar:
identidad → tenant → caso → documento → visibilidad/permisos.

Nunca se expondrán rutas físicas de almacenamiento.

## 6. Datos sensibles

Se minimizarán datos almacenados y se aplicarán políticas de retención.

Los secretos se gestionarán mediante secret management y no se almacenarán en código.

## 7. Auditoría

Se auditarán acciones sensibles.

## 8. Portal Cliente

El cliente sólo verá información expresamente publicada.

La información interna será privada por defecto.

## 9. Seguridad de API

- validación de entrada;
- rate limiting donde proceda;
- protección contra acceso horizontal;
- protección de endpoints administrativos;
- CORS controlado;
- headers de seguridad;
- logs sin secretos.

## 10. Seguridad de archivos

- validación MIME/extensión;
- límites de tamaño;
- nombres internos seguros;
- almacenamiento privado;
- descarga mediante autorización;
- antivirus/escaneo cuando la infraestructura lo permita.

Las mismas reglas aplican a `message_attachments`, no solo a `document_versions` (`ADR-COMMS-001`).

## 11. Aislamiento del Python Worker (IA)

`ADR-AI-001`. El worker Python de OCR/extracción/procesamiento documental:

- no recibe credenciales de PostgreSQL;
- no tiene conectividad de red hacia la base de datos (aplicado a nivel de red/infraestructura, no solo como promesa documental);
- solo se invoca vía AI Gateway/Orchestrator y/o eventos RabbitMQ autorizados;
- entrega resultados exclusivamente a un endpoint interno de Spring Boot, fuera de `/api/v1` público, que valida y persiste en `document_extractions`.

`Python Worker → PostgreSQL` queda **PROHIBIDO**. Cualquier despliegue que otorgue al worker credenciales o conectividad directa a PostgreSQL incumple esta especificación.

## 12. Integraciones

Las credenciales de cualquier `integration` se referencian mediante `credentials_ref` a un secret manager; nunca se almacenan en claro en la base de datos ni en código (`ADR-INTEGRATIONS-001`).
