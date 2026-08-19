# BRIKA V1 — ENGINEERING DOCUMENTATION

Este directorio contiene la especificación consolidada de Brika V1.

`README.md` es el **índice vivo** de la documentación (`ADR-PROCESS-003`). No afirma un estado propio: para el estado documental vigente, ver `10_DOCUMENTATION_STATUS.md`. Para el snapshot de una revisión concreta ya realizada, ver `00_FINAL_REVIEW_README.md`. Para el historial de decisiones arquitectónicas, ver `12_DECISION_LOG.md`.

## Producto y dominio

- `BRIKA_MASTER_SPEC.md` — especificación maestra, fuente de verdad de mayor prioridad.
- `02_PRODUCT_SPECIFICATION.md`
- `FUNCTIONAL_SPECIFICATION.md`
- `03_DOMAIN_SPECIFICATION.md`
- `04_WORKFLOW_SPECIFICATION.md` — conceptual, histórico. Ver versión definitiva abajo.
- `13_DEFINITIVE_WORKFLOW_SPECIFICATION.md` — estados y transiciones de `CASE`, congelado.

## Arquitectura, datos y seguridad

- `03_TECHNICAL_SPECIFICATION.md`
- `04_DATABASE_SPECIFICATION.md` — conceptual, histórico. Ver versión definitiva abajo.
- `15_DEFINITIVE_ERD.md` — ERD conceptual, congelado.
- `16_POSTGRESQL_SCHEMA_SPECIFICATION.md` — esquema físico, congelado. Debe coincidir con el ERD.
- `05_API_SPECIFICATION.md` — principios de API.
- `17_API_SPECIFICATION_DETAILED.md` — endpoints detallados.
- `06_SECURITY_SPECIFICATION.md`
- `05_PERMISSIONS_MATRIX.md` — matriz conceptual, histórica. Ver catálogo definitivo abajo.
- `14_DEFINITIVE_PERMISSION_CATALOG.md` — catálogo de permisos, congelado.
- `07_DATA_GOVERNANCE_SPECIFICATION.md`
- `18_STORAGE_SPECIFICATION.md`
- `19_IDENTITY_OAUTH_SPECIFICATION.md`
- `20_RABBITMQ_SPECIFICATION.md`
- `23_CLOUD_DEPLOYMENT_SPECIFICATION.md`

## Negocio hipotecario

- `06_BANK_ENGINE_SPECIFICATION.md`
- `22_BANK_ENGINE_DETAILED.md`
- `08_SCORING.md`
- `07_PORTAL_CLIENTE.md`
- `09_AI.md`
- `21_AI_V1_SCOPE.md`

## Ingeniería y calidad

- `10_DEVOPS.md`
- `11_TESTING.md`
- `24_TEST_STRATEGY_DETAILED.md`
- `13_ACCEPTANCE_CRITERIA.md`
- `14_IMPLEMENTATION_PLAN.md` — **SUPERSEDED** (`ADR-PROCESS-001`). Se conserva como histórico.
- `25_CLAUDE_CODE_EXECUTION_GUIDE.md` — **plan de ejecución sprint a sprint vigente**, única fuente de verdad de ejecución.

## Control de decisiones, estado y proceso

- `12_DECISION_LOG.md` — histórico de ADR.
- `08_REQUIREMENTS_TRACEABILITY.md`
- `09_ROADMAP.md` — roadmap macro, referenciado contra los sprints de `25`.
- `10_DOCUMENTATION_STATUS.md` — fuente del estado documental vigente.
- `11_CROSS_DOCUMENT_REVIEW.md` — primera revisión cruzada (histórica).
- `26_PRE_CODING_AUDIT.md` — auditoría pre-codificación (histórica, ver nota de actualización dentro del documento).
- `27_KEYCLOAK_REMOVAL_ANALYSIS.md` — análisis y diseño de la sustitución de Keycloak (Sprint 22).
- `28_SPRINT_22_IMPLEMENTATION_REPORT.md` — informe de implementación de la autenticación propia (Sprint 22, Fases 1-6; el cierre que retiró Keycloak se documenta en la adenda de ADR-AUTH-001 y en `GETTING_STARTED.md`).
- `12_DOCUMENT_MANIFEST.md` — hashes SHA-256 de integridad de todos los documentos.
- `CLAUDE.md` — instrucciones de proyecto para agentes de desarrollo.
- `CLAUDE_CODE_START_PROMPT.md` — prompt de arranque de Sprint 0.
- `00_FINAL_REVIEW_README.md` — snapshot fechado de una revisión concreta, no se actualiza tras esa fecha.

## Regla

No se debe programar contra conversaciones aisladas. La documentación versionada es la fuente de verdad. Cualquier decisión nueva se registra en `12_DECISION_LOG.md` antes de implementarse.
