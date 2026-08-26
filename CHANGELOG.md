# Changelog

Formato basado en el historial real del repositorio y en el código verificado — sin
funcionalidades inventadas. Ver `docs/12_DECISION_LOG.md` para el detalle sprint a sprint y
`docs/30_RELEASE_V1.0.0.md` para la definición formal de esta release.

## [1.0.0]

Cierre oficial de Brikka V1.

### Producto

- Gestión multiempresa (multi-tenant), resuelta desde la identidad autenticada.
- Usuarios y RBAC: SUPERADMIN, MANAGER, BROKER, CLIENT (portal como límite de seguridad separado).
- Gestión de clientes (CRM).
- Casos/expedientes hipotecarios, con historial de estado.
- Financiación: simulaciones, ofertas.
- Motor de matching bancario y solicitudes a bancos.
- Comisiones de caso (case fees).
- Análisis financiero y dossier de viabilidad.
- Contratos de encargo (engagement contract).
- Gestión documental: versionado, subida, descarga vía URL presignada, no se sobrescriben
  versiones históricas.
- Extracción de documentos asistida por IA (AI Worker), transporte local por defecto, transporte
  HTTP opcional.
- Tareas.
- Notificaciones (transporte síncrono por defecto; RabbitMQ como transporte asíncrono opcional).
- Catálogo de planes.
- Actividad y auditoría.

### Técnico

- Backend: Spring Boot 3.5.16 (Java 21).
- Frontend: Angular 22.1.
- Base de datos: PostgreSQL 16, migraciones Flyway (26 migraciones).
- Almacenamiento: MinIO (S3-compatible).
- Cola de mensajes: RabbitMQ 3.13 (opcional).
- Autenticación propia: JWT RS256 autoemitido (sin Keycloak), claves internal/portal
  independientes, refresh tokens rotativos con detección de reuso.
- Hashing de contraseñas: Argon2id.
- Multi-tenancy resuelto server-side, nunca confiando en `company_id` del cliente.
- CI real en GitHub Actions: build + tests + lint + build de imagen Docker backend + escaneo Trivy.
- Imágenes Docker backend y frontend, ambas ejecutando como usuario no-root (validado en
  ejecución real, no solo en el Dockerfile).
- Validación fail-closed de configuración de producción (`ProdEnvironmentValidator`): aborta el
  arranque en el perfil `prod` si faltan secretos críticos (claves JWT, credenciales de
  MinIO/RabbitMQ/base de datos, SMTP) o si CORS/seed quedan en un estado inseguro.

### Corregido

- `HttpMessageNotReadableException` (body de request ausente o JSON malformado) devolvía `500
  INTERNAL_ERROR` en cualquiera de los 32 endpoints con `@RequestBody`, en vez de `400
  BAD_REQUEST`. Ahora responde `400` con el contrato de error estándar de la API
  (`{"code":"INVALID_REQUEST", "message", "requestId"}`), reutilizando el mismo formato que el
  resto de errores 4xx.
