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

## Sprint 24 — Entornos, claves JWT, email y seed

### Perfiles Spring (`application-{local,test,prod}.yml`)

`SPRING_PROFILES_ACTIVE` selecciona el perfil; `application.yml` es el baseline común:

- **local**: desarrollo (`./mvnw spring-boot:run`). Email `smtp` → Mailpit; seed habilitado; CORS
  del dev server; claves JWT opcionales (efímeras si vacías).
- **test**: ITs de configuración. Email `test` (sender en memoria); seed deshabilitado.
- **prod**: despliegue real. Email `smtp` siempre (nunca `noop`); seed **prohibido**; claves JWT
  **obligatorias**; CORS estricto desde env.

**PROD es fail-closed**: `ProdEnvironmentValidator` (un `EnvironmentPostProcessor`) aborta el
arranque si falta cualquier secreto o hay una condición insegura (claves JWT ausentes, transporte
no `smtp`, seed activo, CORS con comodines/localhost).

### Claves JWT (RS256 autoemitido)

- Generación: `./scripts/generate-jwt-keys.sh` → `.secrets/jwt/{internal,portal}.private.key`
  (base64 PKCS8 DER). `.secrets/` está en `.gitignore`.
- Carga: `SELF_AUTH_INTERNAL_SIGNING_KEY_PEM` / `SELF_AUTH_PORTAL_SIGNING_KEY_PEM` como secretos
  del orquestador; Internal y Portal usan claves independientes (ADR-PORTAL-AUTH-001).
- Con claves persistentes, los tokens sobreviven reinicios (verificado por test).
- **Rotación**: regenerar ambas claves con el script y re-emitir tokens; documentar el momento del
  corte en este log de despliegue. En PROD no se puede arrancar sin ellas (fail-closed).

### Email SMTP (producción)

Variables (`application-prod.yml`): `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`,
`SMTP_FROM`, `SMTP_FROM_NAME`, `SMTP_TLS`, `SMTP_AUTH`. El transporte en PROD es siempre `smtp`.
`SmtpEmailSender` falla blando (nunca lanza) y registra el error; `ProdEnvironmentValidator` exige
`SMTP_HOST` presente.

### Seed reproducible

`brika.seed.enabled` (+ `@Profile({"local","test"})` + fail-closed en PROD) siembra al arranque, de
forma idempotente, la empresa demo, usuarios `superadmin/manager/broker` y un catálogo de bancos.
Nunca se ejecuta en producción (validación triple). No pisa contraseñas ya fijadas.

## Producción

HTTPS obligatorio.

Acceso a base de datos restringido.

Storage privado.

Principio de mínimo privilegio.
