# BRIKA — Sprint 0 — Getting Started

Este documento cubre únicamente el entorno de infraestructura de Sprint 0 (`ADR-PROCESS-002`, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`). No hay esquema de base de datos, RBAC, lógica de negocio ni API funcional todavía.

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

## 4. Backend (esqueleto, sin lógica de negocio)

```bash
cd backend
./mvnw spotless:check   # formato
./mvnw test             # arranque del contexto Spring
./mvnw spring-boot:run  # expone /actuator/health en :8080
```

```bash
curl http://localhost:8080/actuator/health
```

## 5. Frontend (esqueleto, sin features de negocio)

```bash
cd frontend
npm ci
npm run lint
npm test -- --watch=false
npm run build
npm start   # sirve en :4200
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

Ver `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §3. No hay esquema PostgreSQL, migraciones Flyway, RBAC, lógica de negocio, endpoints funcionales, Portal Cliente, IA ni integraciones. Eso empieza en Sprint 1 y Sprint 2, previa aprobación explícita.
