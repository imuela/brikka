# BRIKA — CLOUD DEPLOYMENT SPECIFICATION V1

## 1. Arquitectura objetivo

Componentes:
- Angular frontend;
- Spring Boot API;
- PostgreSQL;
- RabbitMQ;
- Object Storage;
- proveedor OIDC;
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
- OIDC secrets;
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
