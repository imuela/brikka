# BRIKA — PORTAL CLIENTE V1

## Objetivo

Portal independiente y controlado por el broker para clientes finales.

## Acceso

Autenticación específica de cliente.

## Funcionalidades

### Dashboard
- operaciones;
- estados publicados;
- pendientes;
- notificaciones.

### Operaciones
Consulta de información publicada.

### Documentación
- ver;
- subir;
- sustituir;
- consultar estado;
- responder solicitudes.

### Mensajería
- conversaciones autorizadas;
- mensajes;
- adjuntos permitidos.

La autorización de una conversación tipo CLIENT se evalúa en backend como `tenant + case + participant + visibility` (`ADR-COMMS-002`). Pertenecer a la empresa o al caso no es suficiente: el cliente debe ser `conversation_participant` explícito de esa conversación. El frontend nunca es frontera de seguridad para esta comprobación.

#### Adjuntos en conversaciones del Portal Cliente

Las conversaciones del Portal Cliente admiten adjuntos en V1, usando el mismo `message_attachments` que las conversaciones internas (`ADR-COMMS-001`) — no se convierten en `DOCUMENT` del pipeline documental formal. Cada adjunto queda sujeto a:

- tenant isolation;
- autorización por caso;
- participante autorizado (`conversation_participant`);
- reglas de visibility del Portal Cliente;
- permisos correspondientes (`PORTAL_MESSAGE_ATTACHMENT_UPLOAD`/lectura vía `PORTAL_MESSAGE_READ`);
- validación MIME;
- tamaño máximo;
- checksum;
- almacenamiento seguro (Object Storage privado, mismo patrón de key que el resto de adjuntos);
- nunca exposición directa no autorizada del storage (descarga siempre mediada por el backend, nunca URL directa al proveedor sin autorización).

### Datos
Actualización únicamente de campos autorizados.

## Seguridad

La visibilidad será explícita.

Por defecto:
- notas internas: ocultas;
- comunicaciones internas: ocultas;
- scoring interno: oculto;
- documentación interna: oculta;
- información bancaria interna: oculta.

El broker tendrá control sobre lo que se publica.

## Auditoría

Se registrarán accesos y acciones relevantes.
