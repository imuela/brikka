# BRIKA — Getting Started (Sprint 0 + Sprint 1)

Cubre el entorno de infraestructura (Sprint 0, `ADR-PROCESS-002`) y la foundation de persistencia/Flyway (Sprint 1, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`). El esquema físico completo existe (48 tablas vía Flyway), pero **no hay RBAC funcional, lógica de negocio ni endpoints de negocio todavía** — eso empieza en Sprint 2 en adelante.

## Prerrequisitos

- Docker + Docker Compose
- Java 21 (para `backend/`)
- Node 22 (para `frontend/`)

## 1. Configurar variables de entorno

```bash
cp .env.example .env
```

Edita `.env` si necesitas cambiar puertos o credenciales locales. Nunca commitees `.env`.

## 2. Levantar la infraestructura

```bash
docker compose up -d
```

Servicios y puertos por defecto:

| Servicio | Puerto(s) | Uso |
|---|---|---|
| `postgres` | 15432 | Base de datos (vacía, sin esquema en Sprint 0) |
| `rabbitmq` | 25672 (AMQP), 35672 (management UI) | Mensajería |
| `storage` (MinIO) | 19000 (API S3), 19001 (consola) | Almacenamiento S3-compatible local |
| `identity` (Keycloak) | 18081 (HTTP), 19090 (health) | OIDC local |

`storage-init` es un contenedor de un solo uso (se ejecuta y termina) que crea automáticamente el bucket por defecto (`MINIO_BUCKET`, `brika-documents` salvo que lo cambies) en cuanto `storage` está `healthy`. No requiere ningún paso manual.

Puertos no estándar elegidos deliberadamente para evitar colisiones con otros servicios locales; ajústalos en `.env` si tu máquina los tiene libres y prefieres los estándar (5432, 5672/15672, 9000/9001, 8081).

## 3. Comprobar salud de los servicios

```bash
docker compose ps
```

Todos los servicios deben aparecer como `healthy`. Comprobación manual:

```bash
docker compose exec postgres pg_isready -U brika -d brika
docker compose exec rabbitmq rabbitmq-diagnostics -q ping
curl -f http://localhost:19000/minio/health/live
curl -f http://localhost:19090/health/ready
```

## 4. Backend (Flyway + persistencia, sin lógica de negocio)

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

## 5. Frontend (Sprint 13: foundation, OIDC/PKCE, shell — sin features de negocio)

```bash
cd frontend
npm ci
npm run lint
npm test -- --watch=false
npm run build
npm start   # sirve en :4200
```

Login real contra Keycloak: con la infraestructura del paso 2 levantada (el realm `brika` se
importa automáticamente desde `keycloak/brika-realm.json`, `ADR-FRONTEND-001`), abre
`http://localhost:4200`, pulsa "Iniciar sesión" y usa el usuario de demostración ya sembrado en
el realm:

```
usuario: demo.manager
contraseña: brika_dev_password
```

Ese usuario de Keycloak no tiene por sí solo ninguna fila en `users` — para que `GET /me`
resuelva una identidad hace falta una empresa y un usuario en PostgreSQL cuyo
`external_identity_id` coincida con el `id` fijado en el realm-export
(`11111111-1111-1111-1111-111111111111`):

```sql
INSERT INTO companies (legal_name, trade_name, tax_id, status)
VALUES ('Demo Broker S.L.', 'Demo Broker', 'DEMO-TAX-001', 'ACTIVE') RETURNING id;

INSERT INTO users (company_id, external_identity_id, email, first_name, last_name, status)
VALUES ('<id de la empresa anterior>', '11111111-1111-1111-1111-111111111111',
        'demo.manager@brika.test', 'Demo', 'Manager', 'ACTIVE') RETURNING id;

INSERT INTO user_roles (user_id, role_id)
SELECT '<id del usuario anterior>', id FROM roles WHERE code = 'MANAGER';
```

## 6. Parar el entorno

```bash
docker compose down
```

Para eliminar también los volúmenes (borra datos de los contenedores, empieza de cero):

```bash
docker compose down -v
```

## Qué NO hay todavía

Ver `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §3. El esquema PostgreSQL completo (48 tablas) y las migraciones Flyway ya existen (Sprint 1). NO hay todavía: RBAC funcional, lógica de negocio, entidades JPA, endpoints de negocio, Portal Cliente, IA ni integraciones. Eso empieza en Sprint 2 en adelante, previa aprobación explícita.
