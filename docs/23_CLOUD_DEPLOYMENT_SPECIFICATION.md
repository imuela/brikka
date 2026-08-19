# BRIKA — CLOUD DEPLOYMENT SPECIFICATION V1

## 1. Arquitectura objetivo

Componentes:
- Angular frontend;
- Spring Boot API;
- PostgreSQL;
- RabbitMQ;
- Object Storage;
- autenticación propia de Brika: JWT RS256 autoemitido (Internal/Portal con claves independientes);
- servicios de IA (AI Gateway/Orchestrator + Python Worker);
- observabilidad.

## 2. Contenedores

Cada componente desplegable tendrá imagen Docker reproducible.

### 2.1 Aislamiento de red del Python Worker (`ADR-AI-001`)

El Python Worker (OCR/extracción/procesamiento documental) se despliega en un segmento de red **sin conectividad hacia PostgreSQL**: sin credenciales de base de datos, sin reglas de firewall/security group que permitan la conexión, y sin variables de entorno que expongan el `connection string`. Solo tiene conectividad hacia RabbitMQ y hacia el endpoint interno de Spring Boot donde entrega resultados. Esta restricción se verifica en el hardening de seguridad de Sprint 12 (`25_CLAUDE_CODE_EXECUTION_GUIDE.md`).

## 3. Entornos

- local
- development
- staging
- production

Datos y secretos separados por entorno.

### 3.1 Perfiles Spring (Sprint 24)

`SPRING_PROFILES_ACTIVE` selecciona `application-{local,test,prod}.yml` sobre el baseline común
`application.yml`. **PROD es fail-closed** (`ProdEnvironmentValidator`, un
`EnvironmentPostProcessor`): aborta el arranque si faltan las claves JWT
(`brika.security.self-auth.{internal,portal}-signing-key-pem`), si el email no es `smtp`, si el
seed queda habilitado, o si CORS contiene comodines/localhost.

### 3.2 Autenticación (Sprint 24)

El emisor de identidad es el **propio backend** (JWT RS256 autoemitido), no un proveedor OIDC
externo (Keycloak retirado en Sprint 22). Internal y Portal usan claves RSA independientes. Las
claves deben proveerse como secretos persistentes (nunca efímeras en producción):

- `SELF_AUTH_INTERNAL_SIGNING_KEY_PEM`
- `SELF_AUTH_PORTAL_SIGNING_KEY_PEM`

Generación y rotación: `./scripts/generate-jwt-keys.sh` (véase `10_DEVOPS.md` §Sprint 24).

### 3.3 Email SMTP (Sprint 24)

El transporte en PROD es siempre `smtp` (nunca `noop`, ADR-NOTIF-001). Variables:
`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`, `SMTP_FROM_NAME`,
`SMTP_TLS`, `SMTP_AUTH`.

## 4. CI/CD

Pipeline mínimo:
1. checkout;
2. dependency checks;
3. lint;
4. unit tests;
5. build;
6. integration tests;
7. container build;
8. security scan;
9. deploy staging;
10. smoke tests;
11. aprobación;
12. production.

## 5. Secrets

Nunca almacenar:
- passwords;
- API keys;
- JWT signing keys (`SELF_AUTH_*_SIGNING_KEY_PEM`) y credenciales SMTP;
- DB credentials

en Git.

Usar secret manager del proveedor cloud.

## 6. Database

PostgreSQL gestionado cuando sea viable.

Backups automáticos.

Pruebas periódicas de restore.

## 7. Storage

Object Storage privado.

## 8. Observabilidad

- logs centralizados;
- métricas;
- trazas;
- alertas;
- uptime/health checks.

## 9. Escalado

V1 puede comenzar con una API escalable horizontalmente.

No introducir Kubernetes salvo que la infraestructura real lo justifique.

## 10. Disaster Recovery

Definir:
- RPO;
- RTO;
- backup retention;
- procedimiento de restauración.

Los valores finales dependerán del proveedor y del SLA contratado.
