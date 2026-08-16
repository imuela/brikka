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

### 3.1B SUPPORT_SESSION — acceso de SUPERADMIN a recursos tenant-owned (`ADR-RBAC-001`)

`SUPERADMIN` no tiene acceso cross-tenant permanente a ningún recurso tenant-owned. Su único camino de acceso es una `SUPPORT_SESSION` activa:

- exactamente una `target_company_id` por sesión, nunca `'*'` ni todas las empresas;
- `reason` y `expires_at` obligatorios; no se permiten sesiones indefinidas;
- `status`: `ACTIVE` / `EXPIRED` / `CLOSED`;
- toda acción realizada durante la sesión queda auditada con referencia a la sesión (`support_session_id` en `audit_events`);
- `SUPPORT_SESSION` no cambia el rol de SUPERADMIN ni concede permisos nuevos: solo habilita el uso de los permisos ya marcados `SUPPORT_SESSION` en `ADR-RBAC-001`, y solo contra `target_company_id`;
- al expirar o cerrarse la sesión, el acceso tenant desaparece inmediatamente, sin periodo de gracia.

Mientras `SUPPORT_SESSION` no esté implementado, `TenantContext` debe resolver "sin tenant" para SUPERADMIN en todos los casos, sin excepción, sin fallback y sin bypass — ni por `company_id` recibido del cliente, ni por ausencia de filtro en un repositorio/servicio, ni por un filtro JPA opt-in mal configurado. El mecanismo completo (entidad `support_sessions`, verificación en `TenantContext`, endpoints de apertura/cierre) no forma parte de Sprint 2; el sprint de implementación queda pendiente de asignación explícita en `25_CLAUDE_CODE_EXECUTION_GUIDE.md`.

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
