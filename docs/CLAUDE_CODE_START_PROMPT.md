# CLAUDE CODE — BRIKA START PROMPT

Estás trabajando en BRIKA V1.

Antes de escribir funcionalidad, lee TODOS los archivos de `docs/`.

Tu primera tarea es una auditoría documental y la preparación de Sprint 0.

## Reglas obligatorias

1. No inventes decisiones de arquitectura.
2. No cambies el stack.
3. No implementes funcionalidad de negocio antes de completar Sprint 0.
4. No configures producción todavía.
5. No uses `companyId` proporcionado por el frontend como autoridad de tenant.
6. Todo recurso tenant-owned debe quedar aislado por `company_id`.
7. `BANK` es global.
8. `BANK_CONTACT` pertenece a `COMPANY`, no al broker.
9. El Portal Cliente sólo puede acceder a recursos publicados/autorizados.
10. La IA nunca tendrá SQL directo.
11. Si detectas contradicciones documentales, detente y repórtalas.
12. Toda migración Flyway aplicada es inmutable.
13. No introduzcas microservicios en V1 salvo decisión explícita.

## Sprint 0

Sprint 0 es exclusivamente **infraestructura y entorno de desarrollo** (`ADR-PROCESS-002`). Preparar un entorno local reproducible con:

- estructura inicial de repositorio Angular + Spring Boot (esqueleto, sin lógica de negocio);
- contenedor PostgreSQL **vacío, sin esquema** (Flyway instalado/configurado pero sin ejecutar ninguna migración de negocio);
- contenedor RabbitMQ;
- Object Storage S3-compatible local;
- OIDC local;
- Docker Compose;
- tests de infraestructura (health checks), no de lógica de negocio;
- CI;
- `.env.example`;
- health checks.

Al finalizar Sprint 0, una máquina nueva debe poder levantar el entorno siguiendo el README, con todos los contenedores arriba y en verde, y **ninguna tabla de negocio creada todavía**.

No implementes todavía:
- esquema definitivo de PostgreSQL ni migraciones de negocio (empiezan en Sprint 1, ver `25_CLAUDE_CODE_EXECUTION_GUIDE.md`);
- RBAC funcional (Sprint 2);
- casos hipotecarios completos;
- scoring;
- motor bancario;
- IA de negocio;
- Portal Cliente completo.

Primero entrega un informe de auditoría y la estructura ejecutable de Sprint 0.
