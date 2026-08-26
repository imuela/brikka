# BRIKA — Getting Started

Cubre el entorno de infraestructura local (Sprint 0, `ADR-PROCESS-002`), la foundation de
persistencia/Flyway (Sprint 1) y, desde Sprint 22, la autenticación propia de Brika (ADR-AUTH-001:
**Keycloak está retirado por completo** — el único emisor de tokens es el backend de Brika, con
JWT RS256 autoemitidos y contraseñas Argon2id). Este documento refleja el estado **actual**.

## Prerrequisitos

- Docker + Docker Compose
- Java 21 (para `backend/`)
- Node 22 (para `frontend/`)

## 1. Configurar variables de entorno

```bash
cp .env.example .env
```

Edita `.env` si necesitas cambiar puertos o credenciales locales. Nunca commitees `.env`.

`docker compose` lee `.env` automáticamente (misma carpeta que `docs/docker-compose.yml` — en
realidad la raíz del repo, ver paso 2), pero **el backend y el frontend no lo leen solos**: para
que `./mvnw spring-boot:run` (paso 4) arranque con el perfil `local` (seed de usuarios de
desarrollo incluido) en vez del perfil `default` de Spring, exporta `.env` a tu shell antes de
arrancarlo (Sprint 40 audit: verificado empíricamente — sin este paso, `SPRING_PROFILES_ACTIVE`
nunca llega al proceso y el seed reproducible de §5 no se ejecuta):

```bash
set -a && source .env && set +a
```

## 2. Levantar la infraestructura

El `docker-compose.yml` vive en `docs/` (`docs/docker-compose.yml`) desde el cierre de Sprint 22.

```bash
docker compose -f docs/docker-compose.yml up -d
```

Servicios y puertos por defecto:

| Servicio | Puerto(s) | Uso |
|---|---|---|
| `postgres` | 15432 | Base de datos (esquema completo vía Flyway al arrancar el backend) |
| `rabbitmq` | 25672 (AMQP), 35672 (management UI) | Mensajería |
| `storage` (MinIO) | 19000 (API S3), 19001 (consola) | Almacenamiento S3-compatible local |
| `mailpit` | 11025 (SMTP), 18025 (web UI) | Capturador SMTP local para emails de recuperación de contraseña (Sprint 22 cierre, punto 5) |

**No hay servicio `identity`/Keycloak** — retirado en Sprint 22 (ver `27_KEYCLOAK_REMOVAL_ANALYSIS.md`
y `12_DECISION_LOG.md` ADR-AUTH-001).

`storage-init` es un contenedor de un solo uso (se ejecuta y termina) que crea automáticamente el bucket por defecto (`MINIO_BUCKET`, `brika-documents` salvo que lo cambies) en cuanto `storage` está `healthy`. No requiere ningún paso manual.

Puertos no estándar elegidos deliberadamente para evitar colisiones con otros servicios locales; ajústalos en `.env` si tu máquina los tiene libres y prefieres los estándar (5432, 5672/15672, 9000/9001, 1025/8025).

## 3. Comprobar salud de los servicios

```bash
docker compose -f docs/docker-compose.yml ps
```

Todos los servicios deben aparecer como `healthy`. Comprobación manual:

```bash
docker compose -f docs/docker-compose.yml exec postgres pg_isready -U brika -d brika
docker compose -f docs/docker-compose.yml exec rabbitmq rabbitmq-diagnostics -q ping
curl -f http://localhost:19000/minio/health/live
curl -f http://localhost:18025/readyz   # Mailpit
```

## 4. Backend

```bash
cd backend
./mvnw spotless:check   # formato
./mvnw verify            # unit tests + test de integración Flyway (Testcontainers)
./mvnw spring-boot:run  # migra contra el postgres de docker compose y expone /actuator/health en :8080
```

```bash
curl http://localhost:8080/actuator/health
```

`spring-boot:run` conecta por defecto a `127.0.0.1:15432/brika` (el `postgres` de `docker compose`, paso 2) y ejecuta las migraciones Flyway automáticamente al arrancar. Ajusta `DB_HOST`/`DB_PORT`/`DB_NAME`/`DB_USER`/`DB_PASSWORD` si tu configuración difiere.

### Nota Docker en macOS sin Docker Desktop (Colima, Lima, etc.)

El test de integración (`FlywayMigrationIT`) usa Testcontainers, que espera el socket estándar `/var/run/docker.sock`. Si tu Docker corre vía Colima u otro backend con socket en otra ruta, expórtalo antes de `./mvnw verify`:

```bash
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="/var/run/docker.sock"
```

No hace falta con Docker Desktop estándar ni en el runner de CI.

Colima (y otros backends sin el daemon Ryuk de reaper estándar) también puede necesitar desactivar Ryuk, o Testcontainers se queda esperando indefinidamente al arrancar el primer contenedor de un test:

```bash
export TESTCONTAINERS_RYUK_DISABLED=true
```

Con Ryuk desactivado, los contenedores de test no se limpian automáticamente al terminar la JVM — puede acumularse basura en `docker ps -a` tras varias ejecuciones; bórrala manualmente si hace falta (`docker container prune`). No hace falta con Docker Desktop estándar ni en el runner de CI.

## 5. Frontend

```bash
cd frontend
npm ci
npm run lint
npm test -- --watch=false
npm run build
npm start   # sirve en :4200
```

### Login (Sprint 22 cierre, ADR-AUTH-001): autenticación propia de Brika

`http://localhost:4200/login` es un formulario real de email/contraseña que llama a
`POST /api/v1/auth/login` (y el Portal Cliente a `POST /api/v1/portal/auth/login`). El backend
verifica la contraseña contra el hash **Argon2id** de `user_credentials`/`portal_account_credentials`
y emite un **JWT RS256 autoemitido** por Brika (`UserAccessTokenIssuer`/`PortalAccessTokenIssuer`),
más un refresh token opaco (rotado en cada uso, con detección de reutilización). **No existe ningún
proveedor externo (Keycloak)**: el emisor propio es el único en todos los entornos.

#### Usuarios de desarrollo (8)

El esquema no siembra usuarios: las identidades se crean con los endpoints de negocio existentes
(`POST /api/v1/users` para internos, `POST /api/v1/clients/{id}/portal-account` para Portal) y su
contraseña inicial se fija con el endpoint interno de bootstrap (ver abajo). Para desarrollo local
se usan estos 8 usuarios, todos con la contraseña de desarrollo `brika_dev_password` (solo local —
en cualquier entorno no efímero las credenciales deben fijarse como secretos, nunca en el repo):

| Email | Rol | Tipo | Empresa |
|---|---|---|---|
| `superadmin@brika.local` | SUPERADMIN | nuevo | — (global) |
| `manager@brika.local` | MANAGER | nuevo | Brikka Dev |
| `broker@brika.local` | BROKER | nuevo | Brikka Dev |
| `client@brika.local` | CLIENT (Portal) | nuevo | Dev Client |
| `dev.superadmin@brika.test` | SUPERADMIN | migrado | — (global) |
| `demo.manager@brika.test` | MANAGER | migrado | Demo Broker |
| `demo.broker@brika.test` | BROKER | migrado | Demo Broker |
| `portal.e2e@client.test` | CLIENT (Portal) | migrado | Portal E2E |

Los 4 "migrados" conservan el `external_identity_id` que usaban con Keycloak (la decisión de
mapeo de identidad de ADR-AUTH-001: el `sub` del JWT reutiliza ese valor, sin cambios de esquema);
los 4 "nuevos" usan identificadores generados localmente. Todos han sido verificados
autenticándose contra el emisor propio con Keycloak apagado/eliminado.

#### Fijar la contraseña inicial de un usuario (bootstrap local)

El flujo de creación de usuario (`UserProvisioningService`) no pide contraseña. Para fijar la
contraseña inicial — de un usuario recién creado o de uno migrado desde el modelo Keycloak — se usa
el endpoint **interno** de bootstrap (fuera de `/api/v1`, protegido por un secreto compartido,
nunca expuesto a usuarios finales):

```bash
# interno
curl -X POST http://localhost:8080/internal/auth/users/{userId}/credentials \
  -H "Content-Type: application/json" \
  -H "X-Internal-Auth-Secret: $INTERNAL_AUTH_BOOTSTRAP_SECRET" \
  -d '{"newPassword":"brika_dev_password"}'

# Portal Cliente
curl -X POST http://localhost:8080/internal/auth/portal-accounts/{portalAccountId}/credentials \
  -H "Content-Type: application/json" \
  -H "X-Internal-Auth-Secret: $INTERNAL_AUTH_BOOTSTRAP_SECRET" \
  -d '{"newPassword":"brika_dev_password"}'
```

`INTERNAL_AUTH_BOOTSTRAP_SECRET` se define en `.env` (gitignored). Vacío por defecto: el endpoint
rechaza todo hasta que se fije un secreto local. Esto es una capacidad operativa/administrativa de
desarrollo, **no** una funcionalidad de producto (un endpoint de administración de contraseñas con
RBAC/auditoría/UX sigue siendo una decisión pendiente).

#### Recuperación de contraseña (Mailpit)

`POST /api/v1/auth/password-reset/request` (y el equivalente Portal) genera un token de un solo uso
(1 h, hasheado en BD) y lo envía por email. En local el transporte es **Mailpit**
(`EMAIL_TRANSPORT=smtp` en `.env`): el email cae en `http://localhost:18025` con el enlace
`http://localhost:4200/password-reset?token=...`. El enlace se usa una sola vez; tras confirmar,
los refresh tokens del usuario quedan invalidados y la contraseña anterior deja de servir.

### Entornos (Sprint 24): LOCAL / TEST / PROD

El backend tiene tres perfiles Spring (`application-{local,test,prod}.yml` sobre el baseline común
`application.yml`), activos con `SPRING_PROFILES_ACTIVE`:

| Perfil | Uso | Email | Seed | Claves JWT |
|---|---|---|---|---|
| `local` | desarrollo (`./mvnw spring-boot:run`) | `smtp` → Mailpit | habilitado | opcionales (efímeras si vacías) |
| `test` | ITs de configuración (`@ActiveProfiles`) | `test` → sender en memoria | deshabilitado | opcionales |
| `prod` | despliegue real | `smtp` (siempre, nunca `noop`) | **prohibido** | **obligatorias** |

**PROD es fail-closed** (`ProdEnvironmentValidator`): aborta el arranque si faltan las claves JWT,
si el email no es `smtp`, si el seed queda habilitado, o si CORS contiene comodines/localhost. Esto
garantiza que en producción nunca se arranque con tokens efímeros, correo `noop`, datos de demo o
CORS abierto.

#### Seed reproducible (local)

En `local` el backend siembra al arranque (idempotente, `brika.seed.enabled=true`): la empresa demo
`Brika Demo S.L.` (tax_id `A00000000`), los usuarios `superadmin@brika.local`,
`manager@brika.local` y `broker@brika.local` (con la contraseña de desarrollo `brika_dev_password`),
y un catálogo mínimo de bancos. No pisa contraseñas ya fijadas. Para apagarlo en local:
`SEED_ENABLED=false` en `.env`.

#### Claves JWT persistentes

Las claves RSA (PKCS8 DER en base64) se generan con:

```bash
./scripts/generate-jwt-keys.sh        # escribe .secrets/jwt/{internal,portal}.private.key
```

y se cargan como secretos (nunca en el repo):

```bash
SELF_AUTH_INTERNAL_SIGNING_KEY_PEM="$(cat .secrets/jwt/internal.private.key)"
SELF_AUTH_PORTAL_SIGNING_KEY_PEM="$(cat .secrets/jwt/portal.private.key)"
```

Con ellas, los tokens sobreviven reinicios del backend (en local, si se dejan vacías, se genera un
par efímero por proceso). En PROD son obligatorias (fail-closed).

## 6. Parar el entorno

```bash
docker compose -f docs/docker-compose.yml down
```

Para eliminar también los volúmenes (borra datos de los contenedores, empieza de cero):

```bash
docker compose -f docs/docker-compose.yml down -v
```

> ⚠️ `down -v` borra la base de datos local entera (incluidos los 8 usuarios de desarrollo de §5).
> Para reconstruirlos: arranca el backend (aplica Flyway), crea las identidades con los endpoints
> de negocio y fija sus contraseñas con el bootstrap local (§5).

## Estado del proyecto

Brika V1.0.0 — cierre oficial en Sprint 40. La aplicación — backend, frontend y Portal Cliente —
es funcional de extremo a extremo en local, con CI real verde, imágenes Docker non-root, y
validación fail-closed de producción. Ver `30_RELEASE_V1.0.0.md` para la definición formal de la
release y `12_DECISION_LOG.md` para el detalle sprint a sprint.
