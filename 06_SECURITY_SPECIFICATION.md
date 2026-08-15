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

### 3.1 RBAC permission vs entitlement de plan

Un `RBAC permission` (qué puede hacer un rol) y un `entitlement` (qué funcionalidad ha contratado la empresa mediante su `plan`) son conceptos distintos y se comprueban por separado (`ADR-PLATFORM-001`).

Una funcionalidad limitada por plan requiere ambas comprobaciones: `tenant + permission + entitlement`. Tener el permiso RBAC nunca es suficiente por sí solo cuando la funcionalidad depende de plan.

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
