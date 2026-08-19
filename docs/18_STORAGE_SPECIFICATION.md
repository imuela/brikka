# BRIKA — STORAGE SPECIFICATION V1

## 1. Objetivo

Separar archivos físicos de metadatos de negocio.

## 2. Storage

Object Storage privado compatible con S3 API.

La aplicación no almacenará binarios grandes en PostgreSQL.

## 3. Key lógico

Formato conceptual para documentos formales:

`companies/{companyId}/cases/{caseId}/documents/{documentId}/versions/{versionId}/{safeFilename}`

Formato conceptual para adjuntos de mensaje (`message_attachments`, `ADR-COMMS-001`):

`companies/{companyId}/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentId}/{safeFilename}`

Este mismo patrón y la misma tabla `message_attachments` cubren también los adjuntos subidos desde el Portal Cliente: no existe una tabla ni un key pattern separado para adjuntos de CLIENT. La diferencia está solo en la autorización (`tenant + case + participant + visibility`, ver `06_SECURITY_SPECIFICATION.md` §3.3), no en el almacenamiento.

No usar nombres proporcionados por el usuario como autoridad de seguridad.

## 4. Acceso

- bucket privado;
- URLs temporales;
- permisos mínimos;
- descarga autorizada por backend;
- nunca exponer credenciales del storage al navegador.

## 5. Seguridad

- checksum;
- MIME/type validation;
- límite de tamaño;
- antivirus cuando esté disponible;
- detección de archivos inválidos;
- cifrado en reposo del proveedor;
- TLS en tránsito.

## 6. Versionado

Cada nueva subida crea `DOCUMENT_VERSION`.

Nunca sobrescribir silenciosamente una versión existente.

## 7. Retención

La retención será configurable según política empresarial/legal.

## 8. Eliminación

La eliminación lógica de metadatos no implica necesariamente eliminación física inmediata.

Deberán existir políticas de lifecycle.

## 9. Portal Cliente

El cliente sólo puede acceder a archivos publicados explícitamente y para los que tenga autorización.

## 10. Procesamiento

Subida:
1. autorización;
2. upload;
3. checksum;
4. registro de versión;
5. evento;
6. OCR/antivirus/IA asíncrono cuando corresponda.
