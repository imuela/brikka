# BRIKA — DECISION LOG V1

## ADR-001 — SaaS multiempresa
**Estado:** DECIDIDO  
Brika será multi-tenant.

## ADR-002 — Público objetivo inicial
**Estado:** DECIDIDO  
V1 se orienta a brokers hipotecarios.

## ADR-003 — Portal Cliente
**Estado:** DECIDIDO  
Existirá un Portal Cliente independiente y controlado por el broker.

## ADR-004 — Roles
**Estado:** DECIDIDO  
SUPERADMIN, MANAGER, BROKER y CLIENT.

## ADR-005 — Backend
**Estado:** DECIDIDO  
Java + Spring Boot.

## ADR-006 — Frontend
**Estado:** DECIDIDO  
Angular + TypeScript.

## ADR-007 — Base de datos
**Estado:** DECIDIDO  
PostgreSQL.

## ADR-008 — Migraciones
**Estado:** DECIDIDO  
Flyway.

## ADR-009 — Documentos
**Estado:** DECIDIDO  
Modelo versionado DOCUMENT → VERSION → FILE.

## ADR-010 — Tenant isolation
**Estado:** DECIDIDO  
Obligatorio y aplicado en múltiples capas.

## ADR-011 — Scoring
**Estado:** DECIDIDO  
Scoring explicable y separado de la decisión bancaria.

## ADR-012 — IA
**Estado:** DECIDIDO  
AI Gateway desacoplado del proveedor.

## ADR-013 — Auditoría
**Estado:** DECIDIDO  
Acciones sensibles deben ser trazables.

## ADR-014 — Regla de cambio
**Estado:** DECIDIDO  
Los cambios de arquitectura/reglas deben documentarse antes de implementarse.


## ADR-BANK-001 — Propiedad de contactos bancarios

**Decisión:** `BANK` será global y único en Brika. `BANK_CONTACT` pertenecerá a `COMPANY`, no al broker individual.

**Motivo:** una empresa puede tener varios brokers y debe poder compartir sus contactos bancarios internamente. Los contactos son información operativa propia de cada empresa y no deben exponerse a otras empresas.

**Consecuencia:** el aislamiento de tenant se aplica a `BANK_CONTACT`; las solicitudes bancarias conservarán el contacto utilizado y/o su snapshot histórico.

---

## ADR-BANKENGINE-001 — Bank Matching Engine (motor de evaluación)

**Nota de procedimiento:** este ADR se implementó en Sprint 6B pero nunca se redactó formalmente, pese a ser citado desde entonces por `ADR-SCORING-001 D9-5` ("la fórmula de LTV reutiliza textualmente `ADR-BANKENGINE-001 D-A`") y por el propio `BankMatchingController` (javadoc: "ADR-BANKENGINE-001 §12"). Se reconstruye en Sprint 12 (D12-6) mediante lectura directa del código e íntegramente verificado contra la implementación — mismo procedimiento evidencial que `ADR-SCORING-001`.

**Contexto:** `06_BANK_ENGINE_SPECIFICATION.md` §9 enumera 12 categorías de criterio bancario (ingresos, estabilidad, antigüedad, endeudamiento, LTV, ahorro, edad, tipo de inmueble, finalidad, perfil profesional, garantías, otros) sin definir DSL de reglas, campos evaluables, ni algoritmo de agregación — igual que ocurría con Scoring antes de `ADR-SCORING-001`.

**D-B/D-F — DSL cerrado de 9 operadores sobre exactamente 3 campos evaluables.** `MatchOperator`: `EQUALS`, `NOT_EQUALS`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `IN`, `NOT_IN`, `BETWEEN` — todos sobre valores numéricos. `MatchField`: `LTV` (`computed.ltv`), `REQUESTED_AMOUNT` (`financingRequest.requestedAmount`), `TERM_MONTHS` (`financingRequest.termMonths`) — únicos 3 campos con fuente de datos real en el esquema; las 9 categorías restantes de §9 quedan fuera de alcance por ausencia de dato, no por decisión de producto. `MatchingRule(id, field, operator, value, severity, reason)`, `severity` ∈ `FAIL`/`WARNING`.

**D-G — Validación de reglas en tiempo de escritura, re-validación en tiempo de evaluación como defensa en profundidad.** `CriteriaRulesValidator` rechaza en `POST/PUT` de `BankCriteriaVersion` (payload `{"rules":[...]}`, cada regla exactamente con las 6 claves `id/field/operator/value/severity/reason`, `id` único y con patrón `^[a-z0-9][a-z0-9-]{1,63}$`, `value` con forma coherente con el operador) cualquier regla que el motor no pueda interpretar — "regla desconocida en tiempo de evaluación" es estructuralmente inalcanzable, no defendida en runtime. `BankMatchingService.run()` re-ejecuta el mismo validador antes de evaluar; si falla (corrupción a nivel de BD), persiste `bank_match_results.global_result = ERROR` sin filas de regla, sin lanzar excepción al cliente.

**D-A — Fórmula LTV (heredada literalmente por `ADR-SCORING-001 D9-5`).** `InputSnapshotFactory.computeLtv`: `ltv = requestedAmount / MIN(valuation, purchasePrice)`, con fallback al único denominador disponible si falta uno, `null` si faltan ambos, si `requestedAmount` es `null` o si el denominador es cero; escala 4, `HALF_UP`.

**D-C — Snapshot construido siempre server-side; matching pre-submission, no ligado a `bank_request`.** `InputSnapshotFactory.build(caseId)` carga `Property` y el `FinancingRequest` más reciente del caso — nunca acepta el snapshot desde el cuerpo de la petición. Cero imports cruzados entre `com.brika.platform.bankmatching` y `com.brika.platform.bankrequest` en ningún sentido: el matching es una herramienta de análisis previa a la solicitud formal a banco, independiente de si existe o no un `BankRequest`.

**Algoritmo de evaluación (`MatchingEngine`, puro y determinista, sin I/O):** por regla, si el campo del snapshot es `null` → `NOT_EVALUATED`; si no, `operator.apply(...)` → `PASS` si verdadero, o `severity` (`FAIL`/`WARNING`) si falso. Agregación global (`aggregateResults`, método `public` para ser reutilizado por `ADR-BANKENGINE-002`): precedencia `FAIL > WARNING > NOT_EVALUATED(todas) > PASS`. `MatchResult` tiene 5 valores (`PASS/FAIL/WARNING/NOT_EVALUATED/ERROR`); `ERROR` solo existe a nivel global, nunca por regla.

**Persistencia — append-only, reproducible.** `bank_match_results` (`id, company_id, case_id, bank_id, bank_criteria_version_id, global_result, input_snapshot jsonb, evaluated_by, evaluated_at, created_at`) y `bank_match_rule_results` (`id, match_result_id, rule_id, field, operator, expected_value jsonb, evaluated_value jsonb, result, reason, created_at`) — ambos repositorios exponen únicamente `insert`/`findById`/`findAllByCaseId` (o `findAllByMatchResultId`), sin `update`. Un resultado ya persistido no cambia si `Property`/`FinancingRequest` se modifican después (reproducibilidad, verificada por test).

**Endpoints:** `POST /api/v1/cases/{caseId}/banks/{bankId}/matching` (`BANK_MATCHING_RUN`), `GET /api/v1/cases/{caseId}/matching` (`BANK_MATCHING_READ`), `GET /api/v1/bank-match-results/{id}` (`BANK_MATCHING_READ`, con re-chequeo de tenant enmascarado como 404) — todos vía `CaseAccessService` (TENANT + ROLE/PERMISSION + CASE ASSIGNMENT). Permisos sembrados en `V13__bank_matching_engine.sql`: `SUPERADMIN`/`MANAGER`/`BROKER` para ambos; `CLIENT` sin acceso; `SUPERADMIN` sigue exigiendo `SUPPORT_SESSION` activa (`ADR-RBAC-001`, sin excepción para este módulo).

**Decisiones fuera de alcance (respaldadas explícitamente):** las 9 categorías de criterio bancario sin campo de datos en el esquema (ingresos, estabilidad, antigüedad, endeudamiento, ahorro, edad, tipo de inmueble, finalidad, perfil profesional, garantías); overrides (ver `ADR-BANKENGINE-002` — excluidos explícitamente en el comentario de la propia migración `V13`); ejecución asíncrona (flujo íntegramente síncrono dentro de la request, sin cola ni evento); acoplamiento con `bank_request`.

**Documentos afectados:** este documento (reconstrucción).

**Estado:** APPROVED. Implementado en Sprint 6B (`V13__bank_matching_engine.sql`). Paquete `com.brika.platform.bankmatching` completo (incluyendo `ADR-BANKENGINE-002`): 31 archivos (27 main + 4 test), verificado mecánicamente. La porción atribuible en exclusiva a este ADR (excluyendo los 6 archivos main + 1 test de overrides de `V14`) es de 21 main + 3 test, con la salvedad de que `BankMatchingController`/`BankMatchResultResponse`/`RuleResultResponse` fueron posteriormente tocados en `V14` para exponer resultados efectivos — el reparto no es perfectamente limpio por archivo.

## ADR-BANKENGINE-002 — Bank Matching: mecanismo de overrides

**Nota de procedimiento:** mismo ejercicio de reconstrucción Sprint 12 (D12-6) que `ADR-BANKENGINE-001`, para el mecanismo de corrección manual implementado en Sprint 6C.

**Contexto:** `06_BANK_ENGINE_SPECIFICATION.md` §11 ("Overrides") exige poder corregir manualmente un resultado automático por regla sin alterar el rastro de auditoría, registrando valor anterior, valor nuevo, usuario, fecha, motivo y regla afectada. `V13` (Sprint 6B) excluye explícitamente este mecanismo de su propio alcance (comentario de la migración: "Overrides (D-D) are explicitly out of scope and NOT created here").

**D-D — Historial de overrides append-only, separado de las tablas originales, nunca las muta.** `bank_match_rule_overrides` (`V14`): `id, company_id, bank_match_rule_result_id, previous_result, new_result, reason, overridden_by, overridden_at, created_at`, con `CHECK (previous_result <> new_result)` a nivel de BD. `bank_match_results`/`bank_match_rule_results` (`V13`) permanecen completamente inmutables — el resultado efectivo se deriva siempre en tiempo de lectura, nunca se re-escribe sobre las filas originales. `BankMatchRuleOverrideRepository` expone únicamente `insert`/`findById`/`findAllByRuleResultId` (orden cronológico ascendente) — sin `update`.

**Lógica de "resultado efectivo".** `BankMatchOverrideService.effectiveResult(ruleResult, history)`: sin historial, el resultado original se mantiene; con historial, gana el `newResult` de la entrada más reciente. `effectiveGlobalResult(originalGlobalResult, effectivePerRuleResults)` reutiliza literalmente `MatchingEngine.aggregateResults` (hecho `public` específicamente para este fin) sobre el conjunto de resultados efectivos por regla — nunca un valor cacheado. Solo son overrideables `PASS/FAIL/WARNING/NOT_EVALUATED` (`OVERRIDABLE_RESULTS`); `ERROR` no puede corregirse, consistente con que nunca aparece a nivel de regla.

**Concurrencia optimista en la creación.** `BankMatchOverrideService.create(...)` valida `reason` (no vacío, ≤500 caracteres), que `previousResult`/`newResult` sean valores overrideables y distintos entre sí (`OVERRIDE_NOOP`, 400 si iguales), y recomputa el `effectiveResult` actual desde el historial vivo: si no coincide con el `previousResult` declarado por el llamador, lanza `ConflictException("OVERRIDE_STALE_PREVIOUS_RESULT", ...)` → 409, sin insertar fila. Evita que dos overrides concurrentes basados en el mismo estado "anterior" se pisen silenciosamente.

**Endpoint:** `POST /api/v1/bank-match-rule-results/{ruleResultId}/overrides` (`BANK_MATCHING_OVERRIDE`). Resolución de acceso en 3 saltos: `ruleResultId → BankMatchRuleResult → matchResultId → BankMatchResult → caseId`, vía `CaseAccessService`, con re-chequeo de tenant enmascarado como 404 — mismo patrón de dos/tres saltos que `BankOfferController` (Sprint 6A).

**Permiso restringido a `MANAGER`/`SUPERADMIN` — `BROKER` nunca lo tiene, ni con asignación de caso.** Sembrado en `V14__bank_matching_overrides.sql`: solo `MANAGER`/`SUPERADMIN`. Decisión de negocio implícita en el propio seed (corregir un resultado automático es una acción de supervisión, no operativa), confirmada por test dedicado (`brokerCanNeverOverrideEvenWithCaseAssignment`). `SUPERADMIN` sigue exigiendo `SUPPORT_SESSION` activa, sin excepción.

**Exposición en las respuestas.** `BankMatchingController.toResponse` calcula, para cada regla, el historial completo de overrides y el resultado efectivo, y para el global el `effectiveGlobalResult` — ambos junto a (no en sustitución de) los valores originales inmutables: `BankMatchResultResponse.globalResult` vs `effectiveGlobalResult`; `RuleResultResponse.result` vs `effectiveResult`, más `overrideCount` y la lista completa `overrides`.

**Decisiones fuera de alcance (respaldadas explícitamente):** ninguna notificación al overridear (sin imports de ningún paquete de notificación); ninguna re-creación automática de `bank_request` a partir de un override; ningún operador/campo DSL nuevo (mecanismo ortogonal a `MatchOperator`/`MatchField`).

**Documentos afectados:** este documento (reconstrucción).

**Estado:** APPROVED. Implementado en Sprint 6C (`V14__bank_matching_overrides.sql`), construido estrictamente sobre `ADR-BANKENGINE-001` sin modificar sus filas persistidas.

---

## Cierre de arquitectura y documentación — ADRs de la segunda auditoría

Los siguientes ADR resuelven las inconsistencias detectadas en la auditoría cruzada de documentación (ver `11_CROSS_DOCUMENT_REVIEW.md` y la revisión posterior). Todos quedan **APPROVED** por decisión explícita antes de iniciar Sprint 0.

## ADR-DOC-001 — Document Requirements y File

**Contexto:** `BRIKA_MASTER_SPEC.md` §10 define la cadena `DOCUMENT TYPE → REQUIREMENT → REQUEST → DOCUMENT → VERSION → FILE`. `03_DOMAIN_SPECIFICATION.md` §2 lista `DocumentRequirement` y `File` como entidades conceptuales. El ERD/PostgreSQL definitivos (`15`, `16`) no incluían `REQUIREMENT` como tabla y fusionaban `FILE` dentro de `DOCUMENT_VERSION` sin registrar la decisión.

**Problema:** Sin `DOCUMENT_REQUIREMENT` no es posible determinar automáticamente qué documentación es necesaria para una operación, tal como exige `FUNCTIONAL_SPECIFICATION.md` §11. La fusión de `FILE` no estaba documentada y contradecía `03_DOMAIN_SPECIFICATION.md` sin resolución.

**Decisión:** Reinstaurar `document_requirements` como catálogo versionable de reglas condicionadas (`conditions jsonb`), preparado para depender de tipo de operación, perfil de cliente, banco, producto u otras condiciones futuras sin cambio de esquema. Añadir `requirement_id` nullable en `document_requests` para trazar el origen de cada solicitud. Ratificar que `File` no tiene tabla propia: sus atributos quedan formalmente absorbidos en `document_versions`.

**Alternativas consideradas:**
(a) No modelar `REQUIREMENT` y resolver "documentación necesaria" solo con lógica de aplicación no versionada — descartada por romper la trazabilidad exigida por `07_DATA_GOVERNANCE_SPECIFICATION.md`.
(b) Reinstaurar `files` como tabla independiente de `document_versions` — descartada por no existir ningún caso de uso documentado de una versión con múltiples ficheros.

**Consecuencias:** nueva tabla `document_requirements` y FK `requirement_id`; nuevo permiso de gestión de catálogo; `03_DOMAIN_SPECIFICATION.md` deja de listar `File` como entidad independiente.

**Documentos afectados:** `03_DOMAIN_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`, `14_DEFINITIVE_PERMISSION_CATALOG.md`, `08_REQUIREMENTS_TRACEABILITY.md`.

**Estado:** APPROVED.

## ADR-PLATFORM-001 — Plans / Entitlements / Plan Entitlements / Company Subscriptions

**Contexto:** `BRIKA_MASTER_SPEC.md` §4.1 y `FUNCTIONAL_SPECIFICATION.md` §4 asignan a SUPERADMIN la gestión de planes, suscripciones y consumo/funcionalidades por empresa. El esquema PostgreSQL definitivo no incluía ninguna tabla de plataforma para representarlo.

**Problema:** No existía forma física de representar "empresa X tiene plan Y con funcionalidades Z", ni de diferenciar autorización por rol de autorización por plan contratado.

**Decisión:** Reinstaurar `plans`, `entitlements`, `plan_entitlements` y `company_subscriptions`. Se diferencian explícitamente cuatro conceptos: estado de empresa (`companies.status`), estado de suscripción (`company_subscriptions.status`), permiso RBAC y entitlement de plan. La autorización efectiva para funcionalidades limitadas por plan se evalúa como `tenant + RBAC permission + entitlement`. Facturación y pago automático quedan explícitamente fuera de V1 (PENDIENTE, ver `BRIKA_MASTER_SPEC.md` §18).

**Alternativas consideradas:**
(a) No modelar planes en V1 y tratarlo como configuración estática en código — descartada, contradice explícitamente `MASTER_SPEC`/`FUNCTIONAL_SPEC`.
(b) Integrar pasarela de pago ya en V1 — descartada, ninguna documentación funcional lo exige y añade superficie de riesgo financiero no aprobada.

**Consecuencias:** nueva capa de autorización (permiso **y** entitlement, no solo permiso) sobre el pipeline de autorización existente; nuevas pantallas SUPERADMIN de gestión de planes/suscripciones.

**Documentos afectados:** `03_DOMAIN_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `14_DEFINITIVE_PERMISSION_CATALOG.md`, `17_API_SPECIFICATION_DETAILED.md`, `06_SECURITY_SPECIFICATION.md`, `BRIKA_MASTER_SPEC.md`.

**Estado:** APPROVED.

## ADR-PLATFORM-002 — Companies/Plans/Subscriptions: implementación (D-MASTER-1, D-MASTER-2)

**Contexto:** `ADR-PLATFORM-001` dejó el modelo de datos y las tablas construidas (Sprint 1) pero, salvo `EntitlementResolutionService` (informativo, usado solo por `GET /me`), sin endpoints de gestión. `17_API_SPECIFICATION_DETAILED.md` §4B documenta 7 endpoints (`/plans`, `/plans/{id}`, `/companies/{id}/subscription`, `/companies/{id}/subscription/cancel`) nunca implementados; `05_API_SPECIFICATION.md` §2 lista `/companies` como recurso de primer nivel sin detallar su contrato. `COMPANY_CREATE/READ/UPDATE/SUSPEND/DELETE`, `PLAN_READ/MANAGE`, `SUBSCRIPTION_READ/MANAGE` están sembrados desde `V9` sin ningún endpoint que los consuma.

**D-MASTER-1 — Entitlement-gating: no se inventa ninguna funcionalidad limitada por plan; el mecanismo se demuestra mediante tests, no mediante una feature de negocio nueva.** Evidencia: `EntitlementResolutionService.entitlementValuesForCompany` tiene un único llamador en todo el código (`MeController`, uso puramente informativo en `GET /me`); ningún controlador de ningún módulo (Sprints 2-12) comprueba un entitlement antes de autorizar una acción — `grep` de `requireEntitlement`/`checkEntitlement`/`EntitlementGuard` no devuelve resultados. Ninguna especificación aprobada (`BRIKA_MASTER_SPEC.md`, `FUNCTIONAL_SPECIFICATION.md`, `21_AI_V1_SCOPE.md`) define qué funcionalidad concreta de V1 debería quedar limitada por plan — inventar una (p. ej. "el plan FREE no puede usar IA") sería inventar una regla de negocio no aprobada (`CLAUDE.md` §3). Resolución: Sprint 12.1 no gatea ninguna funcionalidad real por entitlement. En su lugar, `PlanEntitlementIT`-style se extiende con un test E2E que demuestra el mecanismo completo y ya conectado a los nuevos endpoints de escritura: crear compañía → crear plan con un entitlement → `PUT .../subscription` → `GET /me` refleja el entitlement; cambiar de plan vía el mismo `PUT` → `GET /me` refleja el cambio. El mecanismo queda demostrado end-to-end sin inventar la funcionalidad que lo consumiría.

**D-MASTER-2 — `COMPANY_DELETE` es una transición de estado lógica (`status = 'DELETED'`), nunca un `DELETE FROM companies` físico.** Evidencia: (1) los 24+ FK `company_id uuid NOT NULL REFERENCES companies (id)` del esquema (`V1`, `V4`, `V6`, `V7`, `V13`, `V14`) no declaran `ON DELETE CASCADE` — un `DELETE` real fallaría por violación de integridad referencial en cuanto la empresa tuviera cualquier usuario, caso, cliente, etc., que es el caso normal, no el excepcional; (2) el patrón establecido en todo el proyecto es siempre transición de estado, nunca borrado físico: `USER_DISABLE` (no `USER_DELETE`), `CASE_CANCEL` (no `CASE_DELETE`), documentos nunca sobrescritos (`ADR-DOC-001`), `bank_match_results`/`scoring_results` append-only; (3) `companies.status` ya existe como `varchar` sin `CHECK` (a diferencia de `company_subscriptions.status`, que sí lo tiene), dejando espacio para un valor adicional sin migración. Resolución: `DELETE /api/v1/companies/{id}` (verbo HTTP que comunica la intención al consumidor de la API) ejecuta `UPDATE companies SET status = 'DELETED'`, igual que `COMPANY_SUSPEND` ejecuta `status = 'SUSPENDED'`. Ambas transiciones son de sentido único (`ACTIVE → SUSPENDED`, `{ACTIVE, SUSPENDED} → DELETED`); no existe endpoint de reactivación porque ningún permiso `COMPANY_REACTIVATE`/`COMPANY_ACTIVATE` está sembrado — no se inventa uno.

**Contrato de `/companies` (nuevo, sin precedente detallado en `17_API_SPECIFICATION_DETAILED.md`).** Diseñado por analogía directa con `/users` (mismo patrón PATCH-para-datos + endpoint-dedicado-para-transición-de-estado): `GET/POST /companies`, `GET/PATCH /companies/{id}`, `POST /companies/{id}/suspend`, `DELETE /companies/{id}`. Alcance dual de `COMPANY_READ`/`COMPANY_UPDATE` (`GLOBAL` para SUPERADMIN, `TENANT` para MANAGER sobre su propia empresa, ver matriz RBAC): SUPERADMIN opera sobre cualquier `id`; MANAGER solo sobre `id == su propio tenant`, con cualquier otro `id` enmascarado como 404 (mismo criterio que todo lookup cross-tenant del proyecto). `COMPANY_CREATE/SUSPEND/DELETE` son SUPERADMIN-only por ausencia de asignación a MANAGER en `V9` — sin comprobación adicional de tenant, igual que `BankController`.

**Decisiones fuera de alcance (respaldadas explícitamente):** ninguna funcionalidad de negocio gateada por entitlement (D-MASTER-1); reactivación de una empresa `SUSPENDED`/`DELETED` (ningún permiso sembrado la habilita); CRUD de `entitlements`/`plan_entitlements` (§4B no los documenta como recursos con endpoint propio, solo como parte del modelo interno); facturación/pago (`ADR-PLATFORM-001`, ya fuera de V1); migraciones nuevas (ninguna columna/tabla adicional es necesaria — `companies.status` ya admite cualquier valor de texto).

**Documentos afectados:** este documento, `17_API_SPECIFICATION_DETAILED.md` (añade el contrato detallado de `/companies`, ausente hasta ahora).

**Estado:** APPROVED. Implementado en Sprint 12.1.

## ADR-INTEGRATIONS-001 — Integraciones

**Contexto:** `05_API_SPECIFICATION.md` y `14_DEFINITIVE_PERMISSION_CATALOG.md` ya referenciaban `/integrations` e `INTEGRATION_*`; `BRIKA_MASTER_SPEC.md` §17 incluye "integraciones base" en el alcance V1. El esquema definitivo no tenía tabla.

**Problema:** No hay ninguna integración externa concreta definida en V1; construir un sistema genérico completo de integraciones sería sobreingeniería sin caso de uso.

**Decisión:** Reinstaurar únicamente `integrations` como estructura mínima de extensibilidad (catálogo de integraciones configuradas, sin lógica funcional de ejecución). `integration_events` **no** se desarrolla en V1 salvo que surja una dependencia técnica real y se registre un ADR específico para ello. No se introduce ningún proveedor externo concreto no aprobado.

**Alternativas consideradas:**
(a) Eliminar `/integrations` y los permisos `INTEGRATION_*` de toda la documentación — descartada, `MASTER_SPEC` ya compromete "integraciones base" como parte del alcance V1.
(b) Construir `integrations` + `integration_events` + adapters completos ya — descartada por sobredimensionar sin caso de uso concreto.

**Consecuencias:** `/integrations` queda de solo lectura/estado en V1; no se implementa ningún adapter concreto; `integration_events` queda documentado como PENDIENTE condicionado.

**Documentos afectados:** `03_DOMAIN_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `05_API_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`, `BRIKA_MASTER_SPEC.md`.

**Estado:** APPROVED.

## ADR-AUDIT-001 — Activities vs Audit Events

**Contexto:** `FUNCTIONAL_SPECIFICATION.md` §19 declara explícitamente que la actividad funcional es distinta del audit log técnico. El esquema definitivo solo incluía `audit_events`.

**Problema:** No existía tabla para alimentar los dashboards de "actividad reciente" (`FUNCTIONAL_SPECIFICATION.md` §3) sin reutilizar el log de auditoría de seguridad, que tiene control de acceso distinto (`AUDIT_READ`) y una audiencia distinta (cumplimiento vs UI de negocio).

**Decisión:** Mantener `audit_events` (seguridad/cumplimiento, inmutable, acceso restringido por `AUDIT_READ`) y `activities` (timeline funcional de negocio, autorización estándar por recurso, p. ej. `CASE_READ`) como tablas independientes. Ambas se alimentan de los mismos eventos de dominio (RabbitMQ) mediante consumidores separados. Ninguna es una vista derivada de la otra.

**Alternativas consideradas:** proyectar `activities` como vista filtrada de `audit_events` — descartada porque limita el control de acceso granular y mezcla dos audiencias con requisitos de seguridad distintos.

**Consecuencias:** dos consumidores de eventos de dominio en vez de uno; `activities` puede incluir eventos que no son auditables por seguridad (p. ej. "tarea completada").

**Documentos afectados:** `03_DOMAIN_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `20_RABBITMQ_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`.

**Estado:** APPROVED.

### Adenda Sprint 11 — Resolución D11-1 a D11-5

Resuelve, con decisión explícita del promotor del proyecto, el alcance de Sprint 11 (Audit + Reporting + Hardening):

- **D11-1 (SUPPORT_SESSION):** se difiere explícitamente, de nuevo, a un sprint posterior (candidato: Sprint 12 — `09_ROADMAP.md` Fase K). No forma parte del bullet list autoritativo de Sprint 11 (`25_CLAUDE_CODE_EXECUTION_GUIDE.md`). `TenantContext` mantiene sin cambios el comportamiento "SUPERADMIN sin sesión = sin tenant resuelto" en todos los módulos, incluidos los nuevos de este sprint. No se crea `support_sessions`, no se añaden endpoints de apertura/cierre, no se añade `support_session_id` a `audit_events`.
- **D11-2 (Reporting):** `FUNCTIONAL_SPECIFICATION.md` §22 no define contrato de datos suficiente (indicadores, agregaciones, formato) para implementar `/reports` sin inventar contenido de negocio. Reporting funcional queda explícitamente fuera de Sprint 11, pendiente de una decisión de producto futura que defina el catálogo de indicadores V1. `REPORT_READ` permanece sembrado sin cambios (APPROVED SUPPORT_SESSION para SUPERADMIN, APPROVED TENANT para MANAGER, NOT_ASSIGNED BROKER/CLIENT) pero sin ningún endpoint que lo consuma todavía.
- **D11-3 (Exports):** `REPORT_EXPORT` permanece `NOT_ASSIGNED` para los 4 roles, sin cambios. No se implementa ninguna capacidad de exportación (CSV/PDF/XLSX) en Sprint 11.
- **D11-4 (RLS):** Sprint 11 entrega una revisión técnica/documental de RLS (ver §15 actualizado de `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`), no políticas `CREATE POLICY` reales. Ninguna tabla existente se modifica. La conclusión y recomendación quedan documentadas para una decisión de implementación posterior.
- **D11-5 (Audit Events):** se construye la infraestructura de persistencia de `audit_events` (modelo, repositorio, `AuditEventWriter`/`SynchronousAuditEventWriter` — mismo patrón que `ActivityPublisher`/`SynchronousActivityPublisher` de Sprint 3) y un endpoint de lectura SUPERADMIN-only (`AUDIT_READ`, GLOBAL, sin `SUPPORT_SESSION`). Ninguna acción de dominio existente (Sprints 2-10) queda instrumentada para escribir en `audit_events` en este sprint: la documentación ("acciones sensibles", `06_SECURITY_SPECIFICATION.md` §7; "cuando corresponda", `17_API_SPECIFICATION_DETAILED.md` línea 251) no enumera ninguna acción concreta de forma inequívoca. Catálogo de acciones auditables queda pendiente de una decisión de seguridad/producto explícita. Infraestructura preparada, sin escritores de dominio conectados.

**Documentos afectados por esta adenda:** este documento, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`.

**Estado:** APPROVED.

## ADR-AUDIT-002 — Catálogo de auditoría funcional (D11-5, resolución)

**Contexto:** D11-5 dejó la infraestructura de `audit_events` construida pero sin ningún escritor de dominio conectado, señalando que ni `06_SECURITY_SPECIFICATION.md` §7 ("acciones sensibles") ni `17_API_SPECIFICATION_DETAILED.md` (línea 251, "cuando corresponda") enumeran un catálogo cerrado de acciones auditables. Revisión Sprint 12 localiza `FUNCTIONAL_SPECIFICATION.md` §24 "Auditoría funcional" — documento ya aprobado, no descubierto en el análisis de Sprint 11 — que sí enumera un catálogo cerrado de 12 categorías: login, cambios de permisos, cambios relevantes de cliente, cambios de operación, cambios de estado, subida de documentos, revisión documental, descarga de documentos, exportaciones, cambios de configuración, uso sensible de IA, integraciones.

**Decisión — D12-2: catálogo cerrado de 12 categorías, 9 mapeadas a hooks reales, 3 marcadas explícitamente NO_IMPLEMENTABLE.** Ninguna categoría se fuerza a un endpoint inventado. Correspondencia:

| Categoría §24 | Acción(es) auditadas | Endpoint(s) | Estado |
|---|---|---|---|
| Cambios de permisos | `USER_CREATED`, `USER_DISABLED` | `POST /api/v1/users`, `POST /api/v1/users/{id}/disable` | Implementado |
| Cambios relevantes de cliente | `CLIENT_UPDATED` | `PATCH /api/v1/clients/{id}` | Implementado |
| Cambios de operación | `CASE_UPDATED` | `PATCH /api/v1/cases/{id}` | Implementado |
| Cambios de estado | `CASE_STATUS_CHANGED`, `CASE_CANCELLED`, `CASE_REOPENED` | `POST /api/v1/cases/{id}/status`, `.../cancel`, `.../reopen` | Implementado |
| Subida de documentos | `DOCUMENT_VERSION_UPLOADED` | `POST /api/v1/documents/{id}/versions` | Implementado |
| Revisión documental | `DOCUMENT_REVIEWED` | `POST /api/v1/documents/{id}/review` | Implementado |
| Descarga de documentos | `DOCUMENT_DOWNLOADED` | `GET /api/v1/documents/{id}/download`, `.../versions/{versionId}/download` | Implementado |
| Cambios de configuración | `SCORING_RULESET_CREATED` | `POST /api/v1/scoring/rulesets` | Implementado (único cambio de configuración de plataforma con endpoint de escritura hoy) |
| Uso sensible de IA | `AI_SUMMARY_REQUESTED`, `AI_EXPLANATION_REQUESTED`, `AI_DRAFT_MESSAGE_REQUESTED`, `AI_DOCUMENT_EXTRACTION_REQUESTED` | los 4 endpoints `POST .../ai/*` (Sprint 10) | Implementado |
| Login | — | — | NO_IMPLEMENTABLE: la autenticación es un token OIDC externo (Keycloak); no existe endpoint de login propio que interceptar |
| Exportaciones | — | — | NO_IMPLEMENTABLE: `REPORT_EXPORT` permanece `NOT_ASSIGNED` para los 4 roles (D11-3); ninguna capacidad de exportación existe |
| Integraciones | — | — | NO_IMPLEMENTABLE: el paquete `integrations` es GET-only por decisión de Sprint 10 (`ADR-AI-001` adenda); no existe ninguna acción de integración que mute estado |

15 puntos de instrumentación concretos (contando por separado los dos endpoints de descarga) en 9 controladores: `UserController`, `ClientController`, `CaseController`, `DocumentController`, `ScoringRulesetController`, `AiSummaryController`, `AiExplanationController`, `AiDraftMessageController`, `AiDocumentExtractionController`.

**D12-2.1 — Contenido de `metadata`: información mínima, nunca PII completa.** Cada hook registra únicamente identificadores de recurso (UUIDs) y, cuando aporta valor de auditoría sin riesgo, un campo enumerado no sensible (p. ej. `role` en `USER_CREATED`, `oldStatus`/`newStatus` en `CASE_STATUS_CHANGED`). `CLIENT_UPDATED` deliberadamente omite los valores actualizados (nombre/email/teléfono) — solo el `clientId` — para no persistir PII de cliente dentro de `audit_events.metadata`, riesgo señalado explícitamente en el Implementation Plan y no invalidado por ninguna decisión posterior.

**D12-2.2 — `AuditEventWriter.write(...)` pierde el parámetro `requestId`.** Los 9 escritores de dominio no necesitan obtener el `requestId` manualmente: `SynchronousAuditEventWriter` lo captura internamente vía `MDC.get(CorrelationIdFilter.MDC_KEY)`, el mismo mecanismo que ya usa `GlobalExceptionHandler` para correlacionar errores. Cambio de interfaz sin impacto retrocompatible real: `AuditEventWriter` no tenía llamadores fuera del paquete `audit` antes de este sprint (D11-5).

**D12-2.3 — `SUPPORT_SESSION` (D11-1) se difiere formalmente más allá de Sprint 12.** D11-1 había señalado Sprint 12 como candidato. La autorización explícita de Sprint 12 → 12.1 no incluye `SUPPORT_SESSION` en su alcance (16 pasos, ninguno lo menciona). Se re-confirma sin cambios el comportamiento existente: sin `SUPPORT_SESSION`, `SUPERADMIN` no resuelve tenant y no puede ejercer ningún permiso `(SUPPORT_SESSION)`, incluidos los nuevos hooks de auditoría de este ADR sobre recursos tenant-owned. No se crea `support_sessions`, no se añaden endpoints, no se añade `support_session_id` a `audit_events`. Candidato para un sprint futuro no planificado en este documento.

**Decisiones fuera de alcance (respaldadas explícitamente):** ampliar el catálogo de §24 con acciones no listadas (p. ej. auditar lecturas); auditar `SCORING_RULESET_MANAGE` distinto de `create` (no existe endpoint `update`/`delete` de rulesets); cualquier acción de `bankmatching`/`bankrequest`/`portal` (§24 no las nombra explícitamente y forzarlas sería inventar alcance).

**Consecuencias:** `AuditEventWriter` (interfaz) y `SynchronousAuditEventWriter` modificados; 9 controladores modificados; nuevos tests de auditoría por acción (uno por fila de la tabla) más 3 tests E2E cross-módulo que verifican auditoría como parte de un flujo completo.

**Documentos afectados:** este documento, `17_API_SPECIFICATION_DETAILED.md` (nota de estado de auditoría por endpoint, si aplica en una revisión futura).

**Estado:** APPROVED. Implementado y validado en Sprint 12.

## ADR-COMMS-001 — Message Attachments

**Contexto:** `FUNCTIONAL_SPECIFICATION.md` §14 y el permiso `MESSAGE_ATTACHMENT_UPLOAD` (`14_DEFINITIVE_PERMISSION_CATALOG.md` §12) prometen adjuntos en mensajes; no existía tabla de soporte.

**Decisión:** Crear `message_attachments`, independiente del pipeline formal `DOCUMENT` (que exige tipo/requisito/revisión), reutilizando las reglas de storage, checksum, MIME y tamaño definidas en `18_STORAGE_SPECIFICATION.md`.

**Alternativas consideradas:** forzar todo adjunto de chat a pasar por el pipeline `DOCUMENT` — descartada por sobrecargar un flujo pensado para documentación formal con revisión, con un caso de uso (adjuntos de conversación) que no la necesita.

**Consecuencias:** nuevo patrón de storage key para adjuntos de conversación, análogo al de documentos.

**Documentos afectados:** `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `18_STORAGE_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`, `14_DEFINITIVE_PERMISSION_CATALOG.md`.

**Estado:** APPROVED.

**Adenda (cierre final pre-Sprint 0):** se confirma explícitamente que `message_attachments` cubre también las conversaciones del Portal Cliente, con la misma tabla y el mismo patrón de storage que las conversaciones internas — no se crea entidad ni tabla nueva. El acceso exige, en orden: tenant → caso → `conversation_participant` → visibility → permiso (`PORTAL_MESSAGE_ATTACHMENT_UPLOAD`/`PORTAL_MESSAGE_READ`), más las mismas validaciones de storage (MIME, tamaño, checksum, descarga mediada por backend, nunca exposición directa del storage). No se convierten en `DOCUMENT` del pipeline formal. No requiere ADR propio: es una aclaración de alcance de `ADR-COMMS-001`, no una decisión estructural nueva. Documentos actualizados: `07_PORTAL_CLIENTE.md`, `06_SECURITY_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`, `14_DEFINITIVE_PERMISSION_CATALOG.md`, `18_STORAGE_SPECIFICATION.md`.

## ADR-COMMS-002 — Conversation Participants

**Contexto:** `15_DEFINITIVE_ERD.md` §12 ya exigía considerar "participantes" en la autorización de conversaciones, pero no existía tabla que los modelara.

**Problema:** Un `CASE` puede tener varios `CaseClient` (HOLDER/CO_HOLDER/GUARANTOR); sin participantes no hay forma de restringir qué cliente(s) del Portal ven una conversación tipo `CLIENT` concreta cuando hay más de un titular.

**Decisión:** Crear `conversation_participants`, obligatoria para conversaciones tipo `CLIENT`. La autorización backend debe comprobar como mínimo `tenant + case + participant + visibility`; nunca basta con verificar que el cliente pertenece a la empresa. El frontend nunca se considera frontera de seguridad. La visibilidad del Portal Cliente sigue controlada por el backend y por las reglas de publicación de Brika (no por la mera pertenencia al `CASE`).

**Alternativas consideradas:** restringir solo por tipo de conversación + tenant — descartada, insuficiente cuando hay varios clientes en el mismo caso con necesidades de privacidad distintas.

**Consecuencias:** refuerzo explícito en `06_SECURITY_SPECIFICATION.md` y `07_PORTAL_CLIENTE.md` de la regla de autorización de mensajería; para conversaciones tipo `INTERNAL` la restricción sigue siendo implícita vía `CASE_ASSIGNMENT` en V1 (no requiere fila de participante).

**Documentos afectados:** `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `06_SECURITY_SPECIFICATION.md`, `07_PORTAL_CLIENTE.md`, `17_API_SPECIFICATION_DETAILED.md`.

**Estado:** APPROVED.

## ADR-NOTIF-001 — Notifications / Canales

**Contexto:** `FUNCTIONAL_SPECIFICATION.md` §20 prometía canales `IN_APP/EMAIL/PUSH/SMS`; no existía separación entre notificación lógica y entrega por canal.

**Decisión:** V1 implementa únicamente `IN_APP` y `EMAIL`. Se crea `notification_deliveries` para separar la notificación lógica (`notifications`) de cada entrega por canal, con estado independiente por canal. La arquitectura queda preparada para añadir `PUSH`/`SMS` sin cambio estructural (el `channel` ya es un valor de catálogo, no una columna fija), pero no se implementan proveedores ni flujos funcionales de `PUSH`/`SMS` en V1.

**Alternativas consideradas:** implementar los 4 canales en V1 — descartada, ningún proveedor de `PUSH`/`SMS` está aprobado ni contratado, y añadirlos sin proveedor real generaría funcionalidad simulada.

**Consecuencias:** workers de entrega por canal (`IN_APP`, `EMAIL`) consumiendo `notification.requested` vía RabbitMQ.

**Documentos afectados:** `15_DEFINITIVE_ERD.md`, `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `20_RABBITMQ_SPECIFICATION.md`, `FUNCTIONAL_SPECIFICATION.md`, `17_API_SPECIFICATION_DETAILED.md`.

**Estado:** APPROVED.

## ADR-PROCESS-001 — Roadmap único

**Contexto:** existían tres documentos de planificación (`09_ROADMAP.md`, `14_IMPLEMENTATION_PLAN.md`, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`) sin remitirse entre sí, con riesgo de contradicción sobre el orden real de ejecución.

**Decisión:** `25_CLAUDE_CODE_EXECUTION_GUIDE.md` es el único documento autoritativo de ejecución sprint a sprint. `14_IMPLEMENTATION_PLAN.md` pasa a estado `SUPERSEDED` y se conserva como histórico (no se elimina, conforme a `CLAUDE.md` §3). `09_ROADMAP.md` permanece como roadmap macro para negocio/stakeholders, con una tabla de correspondencia explícita Fase ↔ Sprint respecto a `25`.

**Alternativas consideradas:** fusionar los tres documentos en uno solo eliminando dos — descartada, `CLAUDE.md` prohíbe eliminar versiones históricas de documentos sin política explícita.

**Consecuencias:** cualquier futura planificación de ejecución se edita únicamente en `25_CLAUDE_CODE_EXECUTION_GUIDE.md`.

**Documentos afectados:** `09_ROADMAP.md`, `14_IMPLEMENTATION_PLAN.md`, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `10_DOCUMENTATION_STATUS.md`.

**Estado:** APPROVED.

## ADR-PROCESS-002 — Alcance de Sprint 0

**Contexto:** `CLAUDE_CODE_START_PROMPT.md` y `26_PRE_CODING_AUDIT.md` incluían PostgreSQL/RabbitMQ/Storage/OIDC dentro de Sprint 0 sin distinguir infraestructura de lógica de negocio; `25_CLAUDE_CODE_EXECUTION_GUIDE.md` movía la base de datos a Sprint 1, generando una contradicción de alcance entre documentos que gobiernan la ejecución de Claude Code.

**Decisión:** Sprint 0 = infraestructura y entorno de desarrollo exclusivamente: repositorio, estructura inicial, Docker, Docker Compose, contenedor de PostgreSQL (vacío, sin esquema), contenedor de RabbitMQ, almacenamiento S3-compatible local, OIDC/Identity local, health checks, CI, lint/format, documentación técnica mínima necesaria. Sprint 0 **no** incluye esquema definitivo de PostgreSQL, migraciones de negocio, RBAC funcional, lógica de negocio, APIs funcionales ni frontend funcional. Sprint 1 = Flyway + foundation backend (primera migración ejecuta aquí). Sprint 2 = Identity + Tenant + RBAC.

**Alternativas consideradas:** mantener las dos definiciones de Sprint 0 en paralelo — descartada, generaba ambigüedad operativa real y riesgo de que Claude Code ejecutara migraciones de negocio antes de tiempo.

**Consecuencias:** ninguna migración de negocio se ejecuta hasta Sprint 1; ninguna lógica de autorización se implementa hasta Sprint 2.

**Documentos afectados:** `CLAUDE_CODE_START_PROMPT.md`, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `26_PRE_CODING_AUDIT.md`.

**Estado:** APPROVED.

## ADR-PROCESS-003 — README y snapshot de revisión

**Contexto:** `README.md` y `00_FINAL_REVIEW_README.md` tenían roles solapados y contenido contradictorio; `README.md` estaba desactualizado (no listaba los documentos 12–26).

**Decisión:** `README.md` es el índice vivo de toda la documentación del proyecto. `00_FINAL_REVIEW_README.md` es un snapshot fechado de una revisión concreta y no se mantiene actualizado tras esa fecha. `10_DOCUMENTATION_STATUS.md` es la única fuente del estado documental vigente.

**Alternativas consideradas:** eliminar uno de los dos ficheros — descartada, se prefiere reasignar roles antes que borrar histórico de revisión.

**Consecuencias:** ninguna consecuencia técnica; es una decisión de proceso documental.

**Documentos afectados:** `README.md`, `00_FINAL_REVIEW_README.md`, `10_DOCUMENTATION_STATUS.md`.

**Estado:** APPROVED.

## ADR-SCORING-001 — Scoring Engine V1

**Contexto:** `08_SCORING.md`, `BRIKA_MASTER_SPEC.md` §"Componentes" y `FUNCTIONAL_SPECIFICATION.md` describen tres componentes de scoring — Client Score, Property Score, Operation Score — como herramienta explicable de apoyo al análisis hipotecario, con reglas configurables por rule set y versión, resultado con score/rating/fecha/versión/desglose, y conservación histórica para explicar decisiones anteriores. Ninguno de estos documentos define el DSL de reglas, los campos evaluables, el algoritmo de agregación, ni el contrato de los endpoints — igual que ocurría con Bank Matching antes de `ADR-BANKENGINE-001`.

**Estado:** APPROVED. Implementado y validado en Sprint 9 (33 archivos: 28 main + 5 test bajo `com.brika.platform.scoring`, cero migraciones — el esquema `scoring_rulesets`/`scoring_rules`/`scoring_results` ya existía en `V1__initial_schema.sql`). Corrección Sprint 12 (D12-6): el desglose 23+5 publicado originalmente no sumaba 33; recuento verificado mecánicamente (`find … -name "*.java" | wc -l`) confirma 28 main + 5 test.

**Alcance:** Property Score + Operation Score únicamente. Client Score queda fuera de V1 (D9-1). Cálculo determinista y reproducible, sin llamadas de red/BD adicionales ni aleatoriedad dentro del motor de evaluación.

**D9-1 — Client Score excluido; sin nuevo modelo de datos financieros de cliente.** Solo Property Score y Operation Score se implementan. No existe ninguna tabla ni campo de datos financieros de cliente en el esquema (`clients` no los tiene); ningún caso de uso de IA ni de scoring los introduce (ver también D10-3, Sprint 10, que cita esta misma decisión).

**D9-2 — DSL cerrado de 9 operadores, desacoplado de Bank Matching.** `ScoreOperator`: `EQUALS`, `NOT_EQUALS`, `LESS_THAN`, `LESS_THAN_OR_EQUAL`, `GREATER_THAN`, `GREATER_THAN_OR_EQUAL`, `IN`, `NOT_IN`, `BETWEEN`. Deliberadamente independiente de `com.brika.platform.bankmatching.MatchOperator` — sin acoplamiento entre dominios, aunque la disciplina de validación es análoga. Ningún operador puede añadirse sin una nueva decisión ADR. El `weight` de una regla puede ser negativo (penalización).

**D9-3 — Categorías: lista ascendente por `maxScore` con exactamente un catch-all `null` como último elemento.** Validado en escritura (`ScoringRulesValidator`: no vacío, nombres únicos, máx. 50 caracteres, orden ascendente, exactamente un `maxScore=null` y debe ser el último). Resuelto en motor (`ScoringEngine.resolveCategory`): primera categoría (ascendente) cuyo `maxScore >= totalScore`.

**D9-4 — Operation Score limitado a `termMonths` y `requestedAmount`.** Ambos obtenidos exclusivamente de `FinancingRequest` (el más reciente por `case_id`, ya que `financing_requests` no tiene `UNIQUE(case_id)` — mismo criterio "más reciente gana" usado en el resto del proyecto).

**D9-5 — Property Score limitado a `valuation`, `purchasePrice` y `LTV` calculado.** `valuation`/`purchasePrice` de `Property`. La fórmula de LTV reutiliza textualmente `ADR-BANKENGINE-001 D-A`: `ltv = requestedAmount / MIN(valuation, purchasePrice)`, con fallback al único denominador disponible, `null` si no hay ninguno o `requestedAmount` es `null`, escala 4, `HALF_UP` — decisión heredada, no nueva.

**D9-6 — `scoring_rulesets` es GLOBAL, sin `company_id`.** Mismo patrón que `bank_criteria_versions`: el catálogo de reglas no es propiedad de ninguna empresa. `ScoringRulesetController` nunca resuelve tenant (`requireTenant()` no se invoca). `ScoringService.run()` evalúa **todos** los `scoring_ruleset` con `status='ACTIVE'` (D9-11), independientemente de quién los creó.

**D9-7 — Infraestructura mínima de autoría/consulta de `scoring_rulesets`.** `POST /api/v1/scoring/rulesets` (`SCORING_RULESET_MANAGE`, SUPERADMIN-only) y `GET /api/v1/scoring/rulesets` (`SCORING_RULESET_READ`, SUPERADMIN/MANAGER/BROKER — permisos ya sembrados en V9, sin migración nueva). El servicio nunca inventa contenido de negocio (pesos, umbrales, reglas) — únicamente valida contra el DSL cerrado (D9-2/D9-3/D9-4/D9-5) y persiste exactamente lo que el llamante (SUPERADMIN) envía.

**D9-8/D9-9/D9-10:** sin evidencia verificable en código, tests o documentación existente. No se reconstruyen ni se infieren.

**D9-11 — Semántica de ejecución: `run` evalúa cada ruleset ACTIVE contra un único snapshot, persistiendo un `scoring_result` inmutable por ruleset.** `ScoringService.run(companyId, caseId)`: construye un `ScoreInputSnapshot` servidor una sola vez por invocación; para cada `scoring_ruleset` ACTIVE, reevalúa (defensa en profundidad, replicando la práctica de `BankMatchingService`) y persiste un `scoring_result` append-only (nunca `UPDATE`). Una base de datos sin ningún `scoring_ruleset` creado rechaza `run` con `NO_ACTIVE_SCORING_RULESET`. Los resultados ya persistidos no cambian si `Property`/`FinancingRequest` se modifican después (reproducibilidad).

**Endpoints case-scoped:** `POST /api/v1/cases/{caseId}/scoring/run` (`SCORING_RUN`) y `GET /api/v1/cases/{caseId}/scoring/results` (`SCORING_READ`), vía `CaseAccessService` (TENANT + ROLE/PERMISSION + CASE ASSIGNMENT, mismo patrón que todo recurso case-scoped desde Sprint 3). El snapshot siempre se construye server-side — nunca se acepta desde el cuerpo de la petición. Permisos ya sembrados en V9 (`SCORING_RUN`/`SCORING_READ`: SUPERADMIN/MANAGER/BROKER), decisión heredada de `ADR-RBAC-001`, no nueva.

**Decisiones fuera de alcance (respaldadas explícitamente):** Client Score (D9-1); RabbitMQ/procesamiento asíncrono del cálculo (ningún archivo lo referencia — el cálculo es siempre síncrono dentro de la request); modificación de Bank Matching (`scoring` no importa `com.brika.platform.bankmatching` en ningún punto, confirmado por ausencia total de dicho import).

**Relación con Sprint 9:** este ADR documenta íntegramente la implementación ya construida y validada (`SPRINT 9 — VALIDATION GATE`, 240/240 tests). No introduce, modifica ni corrige ningún comportamiento.

**Estado de implementación:** completo, validado, sin commit (pendiente del cierre de baseline Sprint 9-11). Deuda técnica conocida y **no corregida por este ADR**: `POST /scoring/rulesets` con `code`+`version` duplicado devuelve HTTP 500 en lugar de 400 (`uq_scoring_rulesets_code_version` sin manejo de `DataIntegrityViolationException` en `GlobalExceptionHandler`).

**Documentos afectados:** ninguno adicional — este ADR documenta retroactivamente una implementación ya reflejada en `08_SCORING.md`, `BRIKA_MASTER_SPEC.md`, `FUNCTIONAL_SPECIFICATION.md` a nivel de alcance de producto, sin requerir cambios en ellos.

## ADR-AI-001 — Python Worker / pgvector

**Contexto:** `03_TECHNICAL_SPECIFICATION.md` introducía un worker Python y pgvector como componentes auxiliares sin que `BRIKA_MASTER_SPEC.md` los reconociera como decisión de stack aprobada (§15/§20).

**Decisión:** Se ratifica un worker Python **stateless**, especializado en OCR/extracción/procesamiento documental, **sin acceso directo a PostgreSQL ni credenciales de PostgreSQL**, aislado también a nivel de red, invocable únicamente mediante AI Gateway/Orchestrator y/o RabbitMQ. Los resultados se persisten exclusivamente mediante mecanismos internos controlados por Spring Boot (`document_extractions`). `Python Worker → PostgreSQL` queda **PROHIBIDO**. `pgvector` se aprueba como extensión de la instancia PostgreSQL existente, no como base de datos independiente. No se implementan embeddings/RAG salvo que estén expresamente incluidos en un alcance V1 aprobado.

Arquitectura conceptual:
`Angular → Spring Boot → AI Gateway/Orchestrator → RabbitMQ → Python Worker → resultado → Spring Boot → PostgreSQL`

**Alternativas consideradas:** dar acceso directo a BD al worker Python por simplicidad de implementación — rechazada explícitamente, contradice el principio "la IA no accede directamente a la BD" ya vigente para el proveedor de IA (`21_AI_V1_SCOPE.md` §3), que debía extenderse también al worker que la sirve.

**Consecuencias:** aislamiento de red/credenciales exigido en despliegue (DevOps/Cloud), endpoint interno de callback en Spring Boot fuera de `/api/v1` público.

**Documentos afectados:** `BRIKA_MASTER_SPEC.md`, `03_TECHNICAL_SPECIFICATION.md`, `21_AI_V1_SCOPE.md`, `06_SECURITY_SPECIFICATION.md`, `23_CLOUD_DEPLOYMENT_SPECIFICATION.md`.

**Estado:** APPROVED.

### Adenda Sprint 10 — Resolución D10-1 a D10-6

Resuelve, con decisión explícita del promotor del proyecto, los puntos que ADR-AI-001 dejaba abiertos para poder implementar Sprint 10 (AI Gateway + Integrations):

- **D10-1 (permisos):** se conceden `AI_USE`/`AI_DOCUMENT_ANALYZE`/`AI_SUMMARIZE`/`AI_DRAFT_MESSAGE` a `SUPERADMIN`/`MANAGER`/`BROKER` (12 combinaciones, `V15__ai_use_permissions.sql`) — mismo mecanismo que V11/V13/V14, ningún código de permiso nuevo. `AI_MANAGE_CONFIGURATION`/`AI_READ_USAGE` para `MANAGER`/`BROKER` permanecen `PENDING` (fuera de alcance de Sprint 10). Ver tabla IA actualizada más abajo.
- **D10-2 (proveedor IA):** ningún proveedor externo aprobado en V1. Se implementa `AiProvider`/`NoOpAiProvider` (nunca reporta éxito fabricado), mismo patrón que `EmailSender`/`NoOpEmailSender` (Sprint 8).
- **D10-3 (casos de uso):** de `21_AI_V1_SCOPE.md` §2, solo 4 casos de uso tienen contrato suficiente para implementarse sin inventar reglas de negocio: extracción documental (async, vía Worker), resumen/explicación/redacción de mensaje (síncronos, vía `AiProvider`, sin Worker). `get_client_financial_profile` queda explícitamente excluido (consistente con D9-1, Sprint 9: no existe modelo de datos financieros de cliente).
- **D10-4/D10-5 (Worker + transporte):** se construye un Worker Python real y separado (`ai-worker/`), stateless, sin acceso ni credenciales de PostgreSQL (ADR-AI-001 sin cambios). El wiring RabbitMQ real no es implementable sin inventar nombres de exchange/queue/routing-key (`20_RABBITMQ_SPECIFICATION.md` solo documenta el evento `ai.document.analysis.requested` y un sobre genérico). Resolución: `AiTaskDispatcher` con dos implementaciones — `LocalAiTaskDispatcher` (por defecto, in-process, sin red) y `HttpAiTaskDispatcher` (HTTP real hacia el Worker, activable por configuración, no es RabbitMQ real). El endpoint interno de callback (`POST /internal/ai/document-extractions/{id}/callback`, ya previsto en ADR-AI-001) queda protegido por secreto compartido verificado manualmente, fuera de las cadenas de seguridad basadas en JWT.
- **D10-6 (alcance excluido):** `get_client_financial_profile` fuera de alcance (ver D10-3). Ningún caso de uso RAG/embeddings se activa (ADR-AI-001 ya lo dejaba condicionado a aprobación expresa, que no se produce en Sprint 10).

**Documentos afectados por esta adenda:** este documento (tabla IA más abajo), `V15__ai_use_permissions.sql`.

---

## ADR-RBAC-001 — Role-Permission Assignment Matrix

**Contexto:** Sprint 1 dejó `role_permissions` intencionadamente vacía (ver `V3__seed_roles_permissions.sql`): `14_DEFINITIVE_PERMISSION_CATALOG.md` define 110 permisos atómicos y 4 roles (`SUPERADMIN`, `MANAGER`, `BROKER`, `CLIENT`, `ADR-004`), pero ningún documento del proyecto contenía una asignación rol→permiso explícita a nivel atómico. Sprint 2 (Identity + Tenant + RBAC, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`) necesita esa asignación para ser funcional.

**Problema:**
1. Asignar permisos sin base documental suficiente equivale a inventar una regla de negocio (`CLAUDE.md` §3, §10).
2. `05_PERMISSIONS_MATRIX.md` —la única fuente con forma de matriz— está autodeclarada histórica y describe capacidades por recurso de forma cualitativa, no permisos atómicos.
3. `SUPERADMIN` necesita acceder a recursos tenant-owned para dar soporte, pero ningún permiso puede implicar acceso cross-tenant permanente sin violar el aislamiento de tenant (`CLAUDE.md` §6, `ADR-010`).

**Decisión:** Se aprueba la matriz completa de 440 combinaciones rol-permiso (110 permisos × 4 roles) detallada íntegramente más abajo, construida sobre `BRIKA_MASTER_SPEC.md` §4 (capacidades por rol) combinado con §8 (constituyentes explícitos de `CASE`), `14_DEFINITIVE_PERMISSION_CATALOG.md` §19-§21, las ADR previas de este mismo documento, y decisiones explícitas del promotor del proyecto registradas durante el diseño de este ADR. Se introduce `SUPPORT_SESSION` como único mecanismo de acceso de `SUPERADMIN` a recursos tenant-owned.

Verificación mecánica (no manual) previa a esta decisión: 110 permisos en catálogo, 4 roles, 440 combinaciones, 81/71/58/11 `APPROVED` (SUPERADMIN/MANAGER/BROKER/CLIENT), 4/6/6/0 `PENDING`, 25/33/46/99 `NOT_ASSIGNED`, cero duplicados, cero permisos del catálogo ausentes de la matriz, cero permisos inventados.

### Matriz definitiva — 110 permisos × 4 roles

Formato por celda: `ESTADO` y, cuando aplica, `(SCOPE)`. Scopes usados: `GLOBAL` (catálogo/plataforma, sin tenant), `TENANT` (limitado a la propia empresa), `CASE` (limitado además a `case_assignments`/participación), `PORTAL` (Portal Cliente, sujeto a `document_publications`/`conversation_participants`/visibility), `SUPPORT_SESSION` (solo `SUPERADMIN`, exige sesión de soporte activa — ver sección dedicada).

#### Plataforma

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `COMPANY_CREATE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Ciclo de vida de empresa = acción de plataforma, no dato tenant-owned |
| `COMPANY_READ` | APPROVED (GLOBAL) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `COMPANY_UPDATE` | APPROVED (GLOBAL) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `COMPANY_SUSPEND` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | MANAGER no puede autosuspender su empresa |
| `COMPANY_DELETE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |

#### Planes y suscripciones (`ADR-PLATFORM-001`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `PLAN_READ` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Catálogo §3B: "uso exclusivo SUPERADMIN" |
| `PLAN_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |
| `SUBSCRIPTION_READ` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Decisión explícita: gestión de planes/suscripciones exclusiva de SUPERADMIN |
| `SUBSCRIPTION_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |

#### Usuarios

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `USER_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `USER_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | BROKER: necesidad operativa derivada de `TASK_ASSIGN`/`CASE_ASSIGN` |
| `USER_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `USER_DISABLE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |
| `USER_ASSIGN_ROLE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | |

#### Clientes

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `CLIENT_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CLIENT_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CLIENT_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CLIENT_EXPORT` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Acción sensible (exportación de PII), sin base documental suficiente para ningún rol en V1 |
| `CLIENT_DELETE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | No asignado en V1, decisión explícita |

#### Operaciones (`CASE`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `CASE_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | Sin caso previo que scopee la creación |
| `CASE_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Ejemplo canónico del catálogo §21 |
| `CASE_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CASE_ASSIGN` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Acción de supervisión |
| `CASE_CHANGE_STATUS` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CASE_CANCEL` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CASE_REOPEN` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Decisión explícita: exclusivo MANAGER |
| `CASE_EXPORT` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Decisión explícita: exclusivo MANAGER |

#### Inmuebles

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `PROPERTY_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Inferencia derivada: `BRIKA_MASTER_SPEC.md` §8 (PROPERTY es constituyente de CASE) + §4 "operaciones" |
| `PROPERTY_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Inferencia derivada, igual que arriba |
| `PROPERTY_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Inferencia derivada, igual que arriba |
| `PROPERTY_DELETE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |

#### Documentos

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `DOCUMENT_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | CLIENT usa `PORTAL_DOCUMENT_READ` |
| `DOCUMENT_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_REQUEST` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_UPLOAD` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_DOWNLOAD` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_REVIEW` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_APPROVE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_REJECT` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `DOCUMENT_DELETE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | No asignado en V1, decisión explícita; contradice principio de versionado sin sobrescritura |
| `DOCUMENT_PUBLISH` | NOT_ASSIGNED | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | `BRIKA_MASTER_SPEC.md` §7 nombra explícitamente al broker; sin caso de uso de soporte plausible para SUPERADMIN |
| `DOCUMENT_UNPUBLISH` | NOT_ASSIGNED | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Igual que arriba |
| `DOCUMENT_REQUIREMENT_READ` | APPROVED (GLOBAL) | APPROVED (GLOBAL) | APPROVED (GLOBAL) | NOT_ASSIGNED | Catálogo global (`ADR-DOC-001`), lectura abierta a todo rol interno |
| `DOCUMENT_REQUIREMENT_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Gestión de catálogo global, patrón SUPERADMIN-only |

#### Financiación

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `SIMULATION_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `SIMULATION_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `SIMULATION_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `FINANCING_REQUEST_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `FINANCING_REQUEST_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `FINANCING_REQUEST_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `FINANCING_FINALIZE` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Decisión explícita: acción irreversible, exclusivo MANAGER |

#### Bancos

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `BANK_READ` | APPROVED (GLOBAL) | APPROVED (GLOBAL) | APPROVED (GLOBAL) | NOT_ASSIGNED | `BANK` es catálogo global (`15_DEFINITIVE_ERD.md` §9) |
| `BANK_CREATE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Gestión de catálogo global |
| `BANK_UPDATE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |
| `BANK_CRITERIA_READ` | APPROVED (GLOBAL) | APPROVED (GLOBAL) | APPROVED (GLOBAL) | NOT_ASSIGNED | |
| `BANK_CRITERIA_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | |
| `BANK_REQUEST_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `BANK_REQUEST_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `BANK_RESPONSE_REGISTER` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `BANK_OFFER_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `BANK_OFFER_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Visibilidad de CLIENT sobre ofertas publicadas se resuelve vía `PORTAL_CASE_READ` + `document_publications`, nunca este permiso interno |
| `BANK_OFFER_SELECT` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Riesgo medio-alto señalado en revisión: acción de naturaleza similar a `FINANCING_FINALIZE`; se mantiene en BROKER por respaldo explícito de §4.3 "bancos", pero queda anotado como candidato a revisión futura |

#### Contactos bancarios (`ADR-BANK-001`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `BANK_CONTACT_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | Contacto es propiedad de la empresa, no del caso (`ADR-BANK-001`) — scope `TENANT`, no `CASE` |
| `BANK_CONTACT_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | |
| `BANK_CONTACT_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | |
| `BANK_CONTACT_DELETE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | |

#### Tareas

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `TASK_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_UPDATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_ASSIGN` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_COMPLETE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `TASK_DELETE` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Riesgo medio señalado en revisión: más irreversible que el resto del grupo; se mantiene en MANAGER por ser rol de supervisión |

#### Comunicación (`ADR-COMMS-001`, `ADR-COMMS-002`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `CONVERSATION_CREATE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `CONVERSATION_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | CLIENT usa `PORTAL_MESSAGE_READ` + `conversation_participants` |
| `CONVERSATION_PARTICIPANT_MANAGE` | APPROVED (SUPPORT_SESSION) | APPROVED (CASE) | APPROVED (CASE) | NOT_ASSIGNED | Scope `CASE` explícito para MANAGER/BROKER (decisión: "siempre limitado por tenant + acceso al CASE/conversación") |
| `MESSAGE_SEND` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `MESSAGE_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `MESSAGE_ATTACHMENT_UPLOAD` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |
| `MESSAGE_ATTACHMENT_DOWNLOAD` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | |

#### Notificaciones

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `NOTIFICATION_READ` | NOT_ASSIGNED | APPROVED (TENANT) | APPROVED (TENANT) | NOT_ASSIGNED | `notifications.company_id` es `NOT NULL` (`16_POSTGRESQL_SCHEMA_SPECIFICATION.md` §11) y SUPERADMIN "no pertenece necesariamente a una empresa" (`BRIKA_MASTER_SPEC.md` §4.1) — incompatibilidad estructural, no solo ausencia de evidencia |
| `NOTIFICATION_MANAGE` | NOT_ASSIGNED | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | Igual restricción estructural para SUPERADMIN |

#### Actividad (`ADR-AUDIT-001`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `ACTIVITY_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Respeta `CASE_READ` del mismo rol (`ADR-AUDIT-001`) |

#### Scoring

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `SCORING_RUN` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Riesgo medio señalado en revisión: grupo con menor anclaje textual directo (solo inferencia derivada §8, ningún rol lo nombra explícitamente) |
| `SCORING_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Igual que arriba |
| `SCORING_RULESET_READ` | APPROVED (GLOBAL) | APPROVED (GLOBAL) | APPROVED (GLOBAL) | NOT_ASSIGNED | Consecuencia de transparencia/explicabilidad (`BRIKA_MASTER_SPEC.md` §12) |
| `SCORING_RULESET_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Catálogo de reglas versionado, gestión SUPERADMIN-only |

#### Auditoría

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `AUDIT_READ` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Necesidad funcional: única forma de auditar el uso de `SUPPORT_SESSION`; decisión explícita: auditoría restringida a SUPERADMIN |
| `AUDIT_EXPORT` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Igual |

#### IA

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `AI_USE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Resuelto Sprint 10 D10-1 (adenda ADR-AI-001, `V15`); mismo patrón de scope que `SCORING_RUN`/`SCORING_READ` (vía `CaseAccessService`/`DocumentAccessService`); explicación de scoring (sin permiso dedicado) mapeada a este permiso |
| `AI_DOCUMENT_ANALYZE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Igual |
| `AI_SUMMARIZE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Igual |
| `AI_DRAFT_MESSAGE` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | APPROVED (CASE) | NOT_ASSIGNED | Igual |
| `AI_MANAGE_CONFIGURATION` | APPROVED (GLOBAL) | PENDING | PENDING | NOT_ASSIGNED | Configuración de IA es responsabilidad de plataforma (`BRIKA_MASTER_SPEC.md` §13) |
| `AI_READ_USAGE` | APPROVED (GLOBAL) | PENDING | PENDING | NOT_ASSIGNED | Igual |

#### Reporting

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `REPORT_READ` | APPROVED (SUPPORT_SESSION) | APPROVED (TENANT) | NOT_ASSIGNED | NOT_ASSIGNED | `BRIKA_MASTER_SPEC.md` §4.2 "reporting" explícito para MANAGER; no nombrado para BROKER |
| `REPORT_EXPORT` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Acción distinta de "consultar reporting", sensible, sin base suficiente |

#### Integraciones (`ADR-INTEGRATIONS-001`)

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `INTEGRATION_READ` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Fuente: decisión explícita del promotor del proyecto durante el diseño de este ADR, no derivada de documentación previa |
| `INTEGRATION_MANAGE` | APPROVED (GLOBAL) | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Igual |
| `INTEGRATION_EXECUTE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | Catálogo §18: "no se ejercita en V1" |

#### Portal Cliente

| Permiso | SUPERADMIN | MANAGER | BROKER | CLIENT | Notas |
|---|---|---|---|---|---|
| `PORTAL_DASHBOARD_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | Catálogo §19, "Capacidades iniciales" |
| `PORTAL_CASE_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_DOCUMENT_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_DOCUMENT_UPLOAD` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_DOCUMENT_REQUEST_RESPOND` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_MESSAGE_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | Sujeto adicionalmente a `conversation_participants` (`ADR-COMMS-002`) |
| `PORTAL_MESSAGE_SEND` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | Igual |
| `PORTAL_MESSAGE_ATTACHMENT_UPLOAD` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | Igual (`ADR-COMMS-001`) |
| `PORTAL_NOTIFICATION_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_PROFILE_READ` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |
| `PORTAL_PROFILE_UPDATE` | NOT_ASSIGNED | NOT_ASSIGNED | NOT_ASSIGNED | APPROVED (PORTAL) | |

### Reglas de autorización obligatorias

Ningún permiso implica autorización efectiva por sí solo. Toda autorización se evalúa como:

```
TENANT + ROLE/PERMISSION + RESOURCE SCOPE
```

y, cuando el recurso es un `CASE` o deriva de uno:

```
TENANT + ROLE/PERMISSION + CASE ASSIGNMENT
```

Reglas adicionales no expresables únicamente mediante `role_permissions`:

- **Permission + entitlement** (`ADR-PLATFORM-001`): funcionalidad limitada por plan exige además el `entitlement` activo de la suscripción.
- **Permission + participant + visibility** (`ADR-COMMS-002`): conversaciones tipo `CLIENT` exigen `tenant + case + participant + visibility`.
- **Permission + SUPPORT_SESSION activa:** todo permiso marcado `(SUPPORT_SESSION)` exige, además del rol, una `support_session` `ACTIVE` no caducada para la `company_id` objetivo. Sin sesión activa, acceso `DENIED`, aunque `role_permissions` conceda el permiso.
- **CLIENT** queda además sujeto en todo momento a `document_publications` (visibilidad de documentos), `conversation_participants` (mensajería) y a que la información esté expresamente publicada (`06_SECURITY_SPECIFICATION.md` §8) — nunca accede por el mero hecho de pertenecer al `CASE`.

### Tenant isolation

Ningún rol tiene ningún permiso con acceso cross-tenant permanente. `TenantContext` resuelve `company_id` exclusivamente desde la identidad autenticada, nunca desde un valor enviado por el cliente (`CLAUDE.md` §6, `ADR-010`). Para `SUPERADMIN`, `TenantContext` no resuelve ningún tenant salvo que exista una `SUPPORT_SESSION` `ACTIVE` — en cualquier otro momento, `SUPERADMIN` no tiene tenant resuelto y no puede ejercer ninguno de sus permisos `(SUPPORT_SESSION)`, aunque `role_permissions` se los conceda.

### SUPPORT_SESSION — diseño conceptual

Único mecanismo autorizado para que `SUPERADMIN` acceda a recursos tenant-owned.

Entidad conceptual `support_sessions` (no implementada todavía): `id`, `superadmin_user_id`, `target_company_id` (obligatorio, exactamente una empresa, nunca `'*'`), `reason` (obligatorio), `started_at`, `expires_at` (obligatorio, sin sesiones indefinidas), `ended_at` nullable, `status` (`ACTIVE`/`EXPIRED`/`CLOSED`).

Reglas obligatorias: no cambia el rol de `SUPERADMIN`; no concede permisos nuevos; solo habilita el uso de permisos ya marcados `(SUPPORT_SESSION)` en esta matriz, y solo contra `target_company_id`; toda acción durante la sesión genera `AuditEvent` con `support_session_id`; al expirar o cerrarse, el acceso tenant desaparece inmediatamente, sin periodo de gracia.

**`SUPPORT_SESSION` no se implementa en Sprint 2** — `25_CLAUDE_CODE_EXECUTION_GUIDE.md` no lo contempla explícitamente en el alcance de ningún sprint todavía. Los 57 permisos `(SUPPORT_SESSION)` pueden sembrarse en `role_permissions` durante Sprint 2 (la concesión es segura por sí sola: sin `TenantContext` con verificación de sesión, que tampoco se implementa todavía, no hay ningún endpoint que pueda ejercerlos), pero **ningún endpoint o servicio debe consumirlos** hasta que existan `support_sessions`, la verificación de sesión en `TenantContext`, y la columna `support_session_id` en `audit_events`. Sprint 2 sí debe implementar, desde el primer commit de `TenantContext`, la regla "`SUPERADMIN` sin sesión activa = sin tenant resuelto" como comportamiento por defecto.

### Estados PENDING (4 combinaciones)

`AI_MANAGE_CONFIGURATION`/`AI_READ_USAGE` para `MANAGER`/`BROKER`. Las 12 combinaciones `AI_USE`/`AI_DOCUMENT_ANALYZE`/`AI_SUMMARIZE`/`AI_DRAFT_MESSAGE` × `SUPERADMIN`/`MANAGER`/`BROKER` que figuraban aquí quedaron resueltas y sembradas en Sprint 10 (D10-1, adenda ADR-AI-001, `V15__ai_use_permissions.sql`).

### NOT_ASSIGNED

Detalle completo en la matriz de arriba. Categorías: exclusiones estructurales (`*_DELETE` de clientes/documentos/inmuebles, `INTEGRATION_EXECUTE`), exclusiones por incompatibilidad de esquema (`NOTIFICATION_READ/MANAGE` para SUPERADMIN), exclusiones por decisión explícita (`SUBSCRIPTION_*`/`AUDIT_*` fuera de MANAGER; `CASE_REOPEN`/`CASE_EXPORT`/`FINANCING_FINALIZE` exclusivos de MANAGER), y catálogos globales fuera de SUPERADMIN.

**Alternativas consideradas:** (a) conceder a SUPERADMIN acceso directo permanente a recursos tenant-owned por simplicidad de implementación — rechazada explícitamente, viola `ADR-010`/`CLAUDE.md` §6; (b) no modelar `SUPPORT_SESSION` y dejar todos los permisos tenant-owned de SUPERADMIN en `NOT_ASSIGNED` indefinidamente — rechazada, bloquearía cualquier capacidad de soporte sin fecha; (c) implementar `SUPPORT_SESSION` completo ya en Sprint 2 — rechazada, expande el alcance definido para ese sprint en `25_CLAUDE_CODE_EXECUTION_GUIDE.md` sin necesidad inmediata.

**Consecuencias:** `role_permissions` puede poblarse en Sprint 2 con las 221 combinaciones `APPROVED` (81+71+58+11). `TenantContext` de Sprint 2 debe nacer con la regla de `SUPERADMIN` sin sesión. Ningún endpoint puede consumir permisos `(SUPPORT_SESSION)` ni los 16 `PENDING` de IA hasta que existan sus mecanismos respectivos.

**Documentos afectados:** `06_SECURITY_SPECIFICATION.md` (nueva sección `SUPPORT_SESSION` + reglas de scope), `14_DEFINITIVE_PERMISSION_CATALOG.md` (referencia a este ADR), `25_CLAUDE_CODE_EXECUTION_GUIDE.md` (alcance exacto de Sprint 2 respecto a `role_permissions`/`TenantContext`/`SUPPORT_SESSION`), `08_REQUIREMENTS_TRACEABILITY.md` (nuevo `BRK`).

**Estado:** APPROVED.

## ADR-IDENTITY-001 — Nullability de users.company_id (SUPERADMIN sin empresa)

**Contexto:** `BRIKA_MASTER_SPEC.md` §4.1 establece explícitamente que SUPERADMIN "no pertenece necesariamente a una empresa concreta". Sin embargo, la migración ya ejecutada `V1__initial_schema.sql` define `company_id uuid NOT NULL REFERENCES companies (id)` en `users`, y `16_POSTGRESQL_SCHEMA_SPECIFICATION.md` describe la misma columna sin anotación de nullability (NOT NULL implícito por convención del documento). `15_DEFINITIVE_ERD.md` §USER reforzaba la contradicción de forma textual ("Usuario interno de una empresa. Debe pertenecer a una `COMPANY`.").

**Problema:** Un SUPERADMIN no puede existir en el sistema tal como está físicamente modelado sin violar la regla de negocio de origen, o sin inventar una "empresa plataforma" ficticia que el propio negocio no ha definido.

**Decisión:** `users.company_id` pasa a admitir NULL. Regla de asignación:
- SUPERADMIN → `company_id = NULL` (obligatorio, no opcional).
- MANAGER / BROKER / CLIENT → `company_id` sigue siendo obligatorio. La columna deja de imponerlo a nivel de constraint SQL, pero ningún flujo de creación de estos tres roles puede dejarlo vacío (validación de aplicación).

No se crea una "empresa plataforma". No se crea una tabla separada para SUPERADMIN. `company_id` sigue siendo la única columna de pertenencia a tenant para `users`.

La corrección se implementa mediante una nueva migración Flyway incremental (`V8`, primera libre tras `V1`–`V7`, que permanecen inmutables):
1. `ALTER TABLE users ALTER COLUMN company_id DROP NOT NULL;`
2. Índice único parcial `uq_users_email_no_company` sobre `(email) WHERE company_id IS NULL`, para cerrar el hueco de integridad descrito abajo.

No se añade ningún `CHECK` constraint que ate `company_id IS NULL` al rol: el rol vive en `user_roles`/`roles`, no en `users`; esa coherencia se aplica en capa de aplicación (servicio de creación de usuario), no en el esquema.

**Por qué no una "empresa plataforma":** introduciría un tenant sintético sin respaldo en `BRIKA_MASTER_SPEC.md`, complicaría toda condición `WHERE company_id = :tenant` con un valor especial a excluir, y contradice directamente el propio texto de origen ("no pertenece necesariamente a una empresa concreta" ya asume ausencia, no una empresa distinta).

**Impacto sobre TenantContext:** para SUPERADMIN, `TenantContext` no resuelve ningún `company_id` por defecto — `company_id = NULL` en la fila del usuario autenticado se traduce directamente en "sin tenant". Para MANAGER/BROKER/CLIENT, `TenantContext` sigue resolviendo el `company_id` del usuario autenticado, que nunca puede ser NULL en la práctica para esos roles. Esta regla ya estaba descrita en `06_SECURITY_SPECIFICATION.md` §3.1B y `25_CLAUDE_CODE_EXECUTION_GUIDE.md` (Sprint 2); este ADR la ancla ahora también a nivel de esquema físico.

**Impacto sobre autorización:** ningún cambio sobre `ADR-RBAC-001`. Un SUPERADMIN con `company_id = NULL` sigue sin acceso a ningún recurso tenant-owned salvo mediante `SUPPORT_SESSION` activa (no implementado en Sprint 2). `company_id = NULL` no es un bypass de tenant isolation; es la representación física de "SUPERADMIN no tiene tenant propio".

**Impacto sobre integridad de datos:**
- Las columnas que referencian `users(id)` en `V1`/`V6`/`V7` (p. ej. `case_status_history.changed_by`, `document_versions.uploaded_by`, `message.sender_user_id`, `tasks.created_by`) referencian la PK `id`, nunca `company_id`; la FK es estructuralmente inmune a este cambio.
- Hueco detectado y corregido en esta misma migración: el índice único existente `uq_users_company_email ON users (company_id, email)` no detecta emails duplicados entre filas con `company_id IS NULL`, porque SQL trata cada NULL como distinto en un índice único compuesto. Se corrige con `uq_users_email_no_company` en `V8`.
- Hallazgo adjunto, fuera de alcance de este ADR: `external_identity_id` no tiene hoy ningún constraint de unicidad propio (preexistente, independiente de este cambio). Queda señalado como pendiente de decisión separada.

**Alternativas consideradas:** (b) empresa plataforma sintética — descartada, sin respaldo en el negocio, complica toda condición de tenant; (c) tabla `superadmins` separada de `users` — descartada, duplica identidad y complica el futuro lookup `external_identity_id` de OIDC y toda la capa de autenticación, que ya asume una única tabla `users`.

**Consecuencias:** `V8` (nueva migración); actualización de `16_POSTGRESQL_SCHEMA_SPECIFICATION.md` y `15_DEFINITIVE_ERD.md` para reflejar nullability condicionada por rol; nuevos tests de aceptación; sin cambio de scope de Sprint 2 más allá de esta corrección de esquema y la validación de aplicación mínima necesaria para sostenerla.

**Documentos afectados:** `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`, `15_DEFINITIVE_ERD.md`, `08_REQUIREMENTS_TRACEABILITY.md`, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `12_DOCUMENT_MANIFEST.md`.

**Estado:** APPROVED.

## ADR-FRONTEND-001 — Sprint 13: CORS, provisión de Keycloak y librería UI

**Contexto:** El análisis Fase 0 de Sprint 13 (frontend) identificó dos bloqueantes verificados por inspección directa del código, no asumidos de la documentación: (1) `06_SECURITY_SPECIFICATION.md` §9 exige "CORS controlado" pero ningún `CorsConfigurationSource` existía en el backend (`grep` de "Cors"/"cors" en todo `backend/src/main/java` sin resultados) — sin él, ninguna llamada del navegador desde `localhost:4200` a `localhost:8080` funciona; (2) `docker-compose.yml` levantaba Keycloak 26.0 en `start-dev` puro, sin realm, sin client, sin usuarios — `19_IDENTITY_OAUTH_SPECIFICATION.md` exige Authorization Code + PKCE mas no define nombre de realm, `client_id` ni redirect URIs concretos. Ambos bloqueantes fueron presentados explícitamente al promotor del proyecto antes de tocar ningún fichero, con recomendación técnica para cada uno; ambos fueron aprobados con la opción recomendada.

**D1 — CORS: `CorsConfigurationSource` mínimo, orígenes explícitos por variable de entorno, nunca wildcard.** `SecurityConfig.corsConfigurationSource()` restringe a `brika.security.cors-allowed-origins` (`CORS_ALLOWED_ORIGINS`, por defecto `http://localhost:4200`), métodos HTTP estándar, cabeceras `Authorization`/`Content-Type`, expone `X-Request-Id` (correlación de errores ya usada por el frontend), `allowCredentials(false)` — los tokens Bearer nunca son credenciales ambientales (a diferencia de cookies), así que no hace falta. Aplicado a ambos `SecurityFilterChain` (interno y Portal) por consistencia, aunque Sprint 13 solo ejercita el interno — el Portal Cliente (Sprint 19) hereda la capacidad sin trabajo adicional. Verificado con test dedicado (`CorsConfigurationIT`): preflight y petición real desde origen permitido llevan `Access-Control-Allow-Origin`; desde un origen no listado, el preflight se rechaza (403) y la petición real no lleva esa cabecera.

**D2 — Keycloak: realm-export versionado, importado automáticamente.** `keycloak/brika-realm.json` (nuevo, versionado en el repo) define el realm `brika` con un único client `brika-frontend` (público, sin secreto, `pkce.code.challenge.method=S256`, `standardFlowEnabled` únicamente — sin implicit ni resource-owner-password, sin service account), `redirectUris`/`webOrigins` restringidos a `http://localhost:4200`, y un usuario de demostración (`demo.manager`, `id` fijado explícitamente en el JSON para poder sembrar de forma determinista una fila `users.external_identity_id` coincidente en PostgreSQL). `docker-compose.yml` monta el fichero en `/opt/keycloak/data/import/brika-realm.json` y añade `--import-realm` al comando de arranque — reproducible en cualquier máquina, sin pasos manuales de Admin Console. El realm `brika-portal` (`ADR-PORTAL-AUTH-001`) queda deliberadamente sin provisionar — fuera del alcance de Sprint 13 (frontend interno únicamente).

**D2.1 — PKCE implementado a mano, sin librería OIDC de terceros.** `code_verifier`/`code_challenge` (S256) vía Web Crypto API (`crypto.getRandomValues`, `crypto.subtle.digest`), nativa en todo navegador moderno. Decisión técnica (no requiere aprobación de producto): evita una dependencia adicional (`CLAUDE.md` §11, "evitar dependencias innecesarias") para un flujo de ~150 líneas, bien estandarizado (RFC 7636), y da control total sobre el estado (signals, zoneless) sin adaptar una librería pensada para Zone.js.

**D4 — Librería UI: Angular Material.** Comparada contra PrimeNG y Tailwind CSS-solo en el análisis Fase 0 (compatibilidad con Angular 22/zoneless, madurez, a11y, theming, velocidad de desarrollo para Sprints 14-19). Angular Material es mantenida por el propio equipo Angular en el mismo ritmo de release que `@angular/core` — menor riesgo de incompatibilidad en una versión de Angular tan reciente — y cubre exactamente lo necesario (tabla, formularios, diálogos, navegación) sin fricción de integración adicional.

**Decisiones fuera de alcance (respaldadas explícitamente):** provisión del realm `brika-portal` (Sprint 19); `nginx.conf` con fallback SPA para el `Dockerfile` de producción (Sprint 20); activar `tsconfig.json` `"strict": true` — evaluado y activado igualmente en Sprint 13 por ser coste cero sobre un repositorio vacío, no por ser parte de D1/D2/D4.

**Consecuencias:** `SecurityConfig.java` modificado (backend, autorizado explícitamente pese a que el encargo nominal es "frontend"); nuevo fichero `keycloak/brika-realm.json`; `docker-compose.yml`/`.env.example` actualizados; nueva dependencia `@angular/material` (+ `@angular/cdk`) en el frontend.

**Documentos afectados:** este documento, `.env.example`, `docker-compose.yml`, `GETTING_STARTED.md`.

**Estado:** APPROVED. Implementado en Sprint 13.

## ADR-PROCESS-004 — Extensión del roadmap más allá de Sprint 12; definición de Sprint 16

**Contexto:** `ADR-PROCESS-001` fijó `25_CLAUDE_CODE_EXECUTION_GUIDE.md` como "el único plan de ejecución sprint a sprint autoritativo", pero ese documento (y `09_ROADMAP.md`, que se referencia contra él) solo definía Sprint 0 → Sprint 12, terminando en "Sprint 12 — E2E + Security + Performance + Release" — es decir, el plan original consideraba el proyecto completo y listo para release en el Sprint 12, y era enteramente backend (no contemplaba frontend). Los Sprints 13 (frontend foundation), 14 (CRM/Operaciones), 15 (Inmueble/Documentación + auditoría UX/i18n) se ejecutaron y cerraron correctamente, pero como una extensión no documentada del plan: cada uno se autorizó de forma interactiva en su propia sesión, sin que `25_CLAUDE_CODE_EXECUTION_GUIDE.md` se actualizara para reflejarlo. Al solicitarse el inicio de Sprint 16, la Fase 0 correspondiente encontró que no existía ninguna definición oficial de "Sprint 16" en ningún documento del proyecto — se reportó explícitamente como bloqueante (regla `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §"Regla de parada": "Si una decisión necesaria no está documentada... no inventar una solución estructural") y se presentaron opciones candidatas, basadas en capacidades de backend ya existentes sin frontend, para que el responsable del proyecto decidiera.

**Decisión:**

1. El roadmap se extiende deliberadamente más allá del Sprint 12. El objetivo actual del proyecto es completar una V1 funcional y usable completa de Brika (backend + frontend interno + Portal Cliente), no detener artificialmente el desarrollo en el Sprint 12 solo porque era el límite del plan original.
2. Se formalizan retroactivamente, sin reescribir su historia (ya implementados y cerrados, con sus propios ADR — `ADR-FRONTEND-001` para Sprint 13; Sprints 14 y 15 sin ADR dedicado, documentados en sus respectivos informes de cierre de sesión y en `frontend/src/app/features/README.md`), los Sprints 13, 14 y 15 dentro de `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §3, como continuación del mismo documento autoritativo.
3. **Sprint 16 queda definido oficialmente como: Financing / Simulations + Bank Matching / Ofertas (frontend interno).** Objetivo: llevar al frontend interno (Broker/Manager) las capacidades de `financing` (`FinancingRequest`, `Simulation`), `bank`/`bankmatching` (catálogo de bancos, matching) y `bankrequest` (`BankRequest` → `BankResponse` → `BankOffer` → `FinalFinancing`) que ya existen y están operativas en el backend desde los Sprints 5, 6A y 6B — ningún endpoint, entidad ni migración nueva.
4. **Razón de la elección:** es la continuidad natural e inmediata del flujo de negocio ya construido en el frontend (Cliente → Operación → Inmueble → Documentación, Sprints 14-15) hacia su siguiente tramo funcional documentado en `FUNCTIONAL_SPECIFICATION.md` §10 y §16-17 (`CASE → FINANCING REQUEST → BANK REQUEST → BANK RESPONSE → BANK OFFER → FINAL FINANCING`), reutilizando el mismo patrón arquitectónico (sección embebida en `case-detail`, gating por `*appHasPermission` contra el catálogo RBAC real) que Property y Documents ya establecieron.
5. **Explícitamente fuera de Sprint 16, como bloques independientes posteriores:**
   - **Portal Cliente** — separado por tener su propia frontera de seguridad (`CLAUDE.md` §7, `ADR-PORTAL-AUTH-001`) y ser, con diferencia, el bloque de trabajo pendiente más grande (backend 100% implementado desde Sprint 7, 0% de frontend).
   - **Tasks / Communications / Notifications** — bloque operativo independiente, sin relación funcional directa con el flujo de financiación.
   - **Users / Companies / Plans (administración)** — bloque administrativo independiente, no forma parte del flujo de negocio Cliente→Operación→...→Financiación.
   - Ninguna funcionalidad no relacionada con Financing/Simulations/Bank Matching/Offers se añade a Sprint 16 sin autorización explícita adicional.

**Alternativas consideradas:** detener el proyecto en el estado actual (Sprint 15) y considerar V1 completa sin frontend para Financing/Bank/Portal — descartada explícitamente por el responsable del proyecto, que autorizó continuar. Implementar Portal Cliente como Sprint 16 en su lugar — descartada por su mayor complejidad/alcance (frontera de seguridad separada) frente a la continuidad más directa que ofrece Financing/Bank Matching sobre el frontend ya existente.

**Consecuencias:** `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §3 pasa a incluir Sprint 13, 14, 15 y 16 (Sprints 17+ quedan sin definir hasta que se repita este mismo proceso — Fase 0, opciones candidatas, decisión explícita — al cierre de Sprint 16). Ningún código de producción, migración ni configuración se modifica por este ADR — es exclusivamente una decisión de alcance/documentación.

**Documentos afectados:** este documento, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`.

**Estado:** APPROVED.

## ADR-PROCESS-005 — Proceso permanente de 3 fases por sprint; definición de Sprint 17

**Contexto:** los Sprints 13-16 se ejecutaron cada uno con su propio ciclo de autorización ad hoc (alcance variable de detalle, pausas intermedias no siempre consistentes entre sesiones). Al iniciar la planificación del siguiente bloque de trabajo, el responsable del proyecto formalizó un proceso único y permanente, aplicable a todo sprint futuro sin excepción, en tres fases obligatorias y secuenciales: **DEFINICIÓN** (análisis del estado real del proyecto — backend/frontend/permisos/tests verificados contra código, nunca asumidos desde la documentación — y propuesta de alcance con justificación, sin modificar código, con parada obligatoria para revisión); **IMPLEMENTACIÓN** (una vez aprobado el alcance, ejecución continua de todo el alcance autorizado sin sub-sprints ni pausas de autorización intermedias entre bloques, deteniéndose únicamente ante un bloqueo real de los enumerados en `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §"Regla de parada"); **CIERRE** (pruebas, hardening, auditoría de seguridad/tenant/UX, informe final, y solo entonces commit/tag/push, siempre sujeto a revisión y autorización explícita del responsable del proyecto antes de cerrar).

**Decisión:**

1. Se adopta el proceso de 3 fases (Definición → Implementación → Cierre) como procedimiento permanente para todo sprint del proyecto a partir de este punto, sustituyendo la variabilidad ad hoc de los Sprints 13-16.
2. **Sprint 17 queda definido oficialmente como: Tasks + Comunicaciones internas + Notificaciones (frontend interno), excluyendo explícitamente Portal Cliente y administración (Users/Companies/Plans).** Objetivo: llevar al frontend interno (Broker/Manager) las capacidades de `task`, `communication` (`Conversation`/`Message`/`MessageAttachment`, INTERNAL y CLIENT, sin SYSTEM) y `notification` (lectura/marcado-como-leída, sin productor) que ya existen y están operativas en el backend desde el Sprint 8 — ningún endpoint, entidad, migración ni permiso nuevo.
3. **Razón de la elección**, seleccionada mediante Fase 0/Definición sobre las tres opciones candidatas identificadas (Tasks/Communications/Notifications; Portal Cliente; administración Users/Companies/Plans): es la continuación más coherente del frontend interno ya construido (mismo patrón de sección embebida en `case-detail` + vista tenant-wide en el shell, mismo gating `*appHasPermission` contra el catálogo RBAC real de los Sprints 2/8, cero fricción de seguridad adicional); Portal Cliente ya estaba comprometido a Sprint 19 en `frontend/src/app/auth/README.md` antes de esta decisión; administración (Users/Companies/Plans) se había diferido explícitamente en el propio Sprint 16 (`ADR-PROCESS-004`, punto 5) como bloque independiente sin relación con el flujo operativo día a día de Broker/Manager que Tasks/Communications/Notifications sí completa.
4. **Explícitamente fuera de Sprint 17, como bloques independientes posteriores:**
   - **Portal Cliente (Sprint 19)** — frontera de seguridad separada (`CLAUDE.md` §7, `ADR-PORTAL-AUTH-001`), realm Keycloak `brika-portal` sin aprovisionar, compromiso ya registrado en `frontend/src/app/auth/README.md`.
   - **Users / Companies / Plans (administración) (Sprint 18 candidato)** — bloque administrativo independiente, diferido ya en `ADR-PROCESS-004`.
   - **Entitlements**, **conversaciones SYSTEM**, **cualquier productor de notificaciones (RabbitMQ u otro)** — no forman parte del alcance autorizado.
5. **Se confirma explícitamente, sin intentar implementarlo en este sprint:** la arquitectura de entrega por RabbitMQ descrita en `ADR-NOTIF-001` (workers de canal `IN_APP`/`EMAIL` consumiendo `notification.requested`) **no está implementada**. La escritura de `notifications`/`notification_deliveries` es, allí donde el backend la ejercita, 100% síncrona e in-process; no existe ningún productor de dominio (ningún módulo Sprint 2-16 escribe en `notifications`) ni dependencia de RabbitMQ activa para este flujo. Sprint 17 construye una UI de lectura honesta sobre este estado real (estado vacío correcto, sin datos ni productores inventados) y dejar constancia explícita de esta brecha entre `ADR-NOTIF-001` y la implementación real es, en sí mismo, parte del alcance autorizado de este ADR.

**Alternativas consideradas:** incluir Portal Cliente en Sprint 17 en lugar de Tasks/Communications/Notifications — descartada, el compromiso a Sprint 19 ya estaba registrado y Portal Cliente requiere aprovisionar un realm Keycloak completo, muy por encima del alcance de "continuación directa del frontend interno". Incluir administración (Users/Companies/Plans) — descartada por la misma razón que en `ADR-PROCESS-004`: no forma parte del flujo operativo Cliente→Operación→...→Financiación→Tareas/Comunicaciones que el frontend interno viene completando sprint a sprint.

**Consecuencias:** `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §3 pasa a incluir Sprint 17; `09_ROADMAP.md` Fase L se actualiza para reflejar que Tasks/Communications/Notifications ya no es un bloque pendiente sin fecha, sino Sprint 17 cerrado, dejando Portal Cliente (Sprint 19) y administración (Sprint 18 candidato) como los dos bloques independientes restantes. Todo sprint futuro (Sprint 18 en adelante) sigue obligatoriamente el proceso de 3 fases de este ADR.

**Documentos afectados:** este documento, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `09_ROADMAP.md`.

**Estado:** APPROVED.
