# BRIKA — DEVOPS & INFRASTRUCTURE V1

## Entornos

- local
- development
- staging
- production

## Contenedores

Docker para desarrollo y despliegues reproducibles.

## Servicios iniciales

- frontend;
- backend;
- PostgreSQL;
- almacenamiento compatible con S3;
- servicios auxiliares cuando sean necesarios.

## CI/CD

Pipeline:
1. checkout;
2. build;
3. lint;
4. unit tests;
5. integration tests;
6. security checks;
7. package;
8. deploy según entorno.

## Migraciones

Flyway ejecutará migraciones de forma controlada.

## Backups

Producción deberá contar con:
- backups automáticos;
- política de retención;
- pruebas periódicas de restauración.

## Observabilidad

- logs;
- métricas;
- health checks;
- alertas;
- correlation IDs.

## Secretos

Nunca se almacenarán secretos en Git.

## Producción

HTTPS obligatorio.

Acceso a base de datos restringido.

Storage privado.

Principio de mínimo privilegio.
