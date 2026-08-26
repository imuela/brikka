# Brikka V1.0.0 — Definición de release

Sprint 40 (cierre oficial de Brikka V1). Este documento formaliza qué constituye la release
`v1.0.0`, con evidencia real recogida en `docs/12_DECISION_LOG.md` (adendas de Sprints 38, 39 y
40).

## Commit y tag

La release `v1.0.0` es el commit apuntado por el tag Git `v1.0.0` en `origin/main`, creado
únicamente después de que la CI real de ese commit terminó verde (ver `12_DECISION_LOG.md`, Sprint
40, Gate 24/25). El tag es la referencia autoritativa — no un hash embebido en este documento, que
quedaría desactualizado en cuanto el repositorio avance.

## Qué incluye

**Producto** (verificado contra el código real, no una lista aspiracional):
gestión multiempresa (multi-tenant); usuarios y RBAC (SUPERADMIN/MANAGER/BROKER/CLIENT); gestión de
clientes (CRM); casos/expedientes hipotecarios; financiación y simulaciones; matching y solicitudes
bancarias; comisiones de caso (case fees); análisis financiero y viabilidad (dossier de
viabilidad); contratos de encargo (engagement contract); gestión documental con versionado y
descarga vía URL presignada; extracción de documentos asistida por IA (AI Worker, transporte local
por defecto); tareas; notificaciones (síncronas por defecto, RabbitMQ opcional); portal de cliente
como límite de seguridad separado; catálogo de planes; autenticación propia (JWT RS256
autoemitido, sin Keycloak); actividad/auditoría.

**Técnico**: Spring Boot 3.5.16 (Java 21) + Angular 22.1 + PostgreSQL 16 (Flyway, 26 migraciones) +
MinIO (S3-compatible) + RabbitMQ 3.13 (opcional, notificaciones async) + Mailpit (solo desarrollo);
JWT RS256 propio con refresh tokens rotativos; multi-tenancy resuelto desde la identidad
autenticada, nunca desde el cliente; CI real en GitHub Actions (build, tests, lint, Docker,
Trivy); imágenes Docker backend y frontend non-root; validación fail-closed de configuración de
producción (`ProdEnvironmentValidator`).

## Qué NO incluye

- Despliegue en ningún VPS ni entorno de producción real — este sprint prepara la configuración y
  documentación, no despliega (ver `10_DEVOPS.md` y la sección de preparación de despliegue de
  este mismo release).
- Orquestación (Kubernetes), IaC (Terraform/Ansible) ni CI/CD adicional (Jenkins) — explícitamente
  fuera de alcance, no se introduce infraestructura no necesaria.
- Escaneo de la imagen Docker `frontend` en CI (solo se construye/escanea `backend`) — gap
  documentado, no bloqueante.
- Bean Validation (`@Valid`) — no existe en el código; la validación de negocio se hace vía
  `ValidationException` de dominio, ya con su propio manejador HTTP.

## Estado de CI

Ver `12_DECISION_LOG.md`, Sprint 40, Gate 24 para el resultado real (job por job) del commit exacto
que este tag apunta. La release solo se etiqueta con CI real verde — nunca con un resultado asumido
o "debería pasar".

## Estado de seguridad

- Trivy sobre las imágenes Docker reales: backend y frontend, `0 CRITICAL / 0 HIGH` (Sprint 39,
  reconfirmado si este sprint reconstruye las imágenes — ver Gate correspondiente).
- Ambas imágenes Docker corren con usuario no-root, validado en ejecución real (no solo en el
  Dockerfile).
- `ProdEnvironmentValidator` fail-closed: aborta el arranque en el perfil `prod` si faltan claves
  JWT, credenciales de MinIO/RabbitMQ/base de datos, SMTP, o si CORS/seed quedan en un estado
  inseguro (Sprint 39, D39-5).
- Sin secretos versionados (`git ls-files`/`git diff` auditados en cada sprint de cierre).

## Limitaciones conocidas y deuda técnica

Ver la sección "Deuda técnica conocida" del informe de este sprint (`12_DECISION_LOG.md`) para el
inventario completo con ID/prioridad/evidencia/impacto — no se duplica aquí para evitar que este
documento y el decision log diverjan con el tiempo.
