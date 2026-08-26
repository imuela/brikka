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

### Adenda Sprint 33 — D33-1: `get_client_financial_profile` deja de estar excluido

D10-3/D10-6 excluían este caso de uso citando D9-1 ("no existe modelo de datos financieros de
cliente"). Sprint 30 introdujo `ClientFinancialProfile`, por lo que el motivo de la exclusión ya
no aplica — no se trata de reabrir D9-1 (Client Score sigue fuera de alcance), sino de reconocer
que su premisa quedó obsoleta. Sprint 33 implementa, sobre esa base, detección de inconsistencias
de renta mensual entre `DocumentExtraction` y `ClientFinancialProfile` (tolerancia ±50€,
solo-lectura, la IA nunca decide cuál valor es correcto ni escribe en el perfil). No se reabre
ningún otro punto de D10-3/D10-6.

### Adenda Sprint 34 — D34-AI-1: generalización de proveedor + Ollama local

**Contexto:** Sprint 33 dejó el Worker (`ai-worker/main.py`) con un único proveedor real
hardcodeado (Anthropic, activado solo por la presencia de `ANTHROPIC_API_KEY`). El encargo de
Sprint 34 pide poder ejecutar análisis de IA en local, gratis y sin API key, sin romper la
abstracción existente ni obligar a ningún proveedor.

**Decisión:** Se generaliza la selección de proveedor dentro del propio Worker Python mediante
`_resolve_provider()` y la variable de entorno `AI_PROVIDER` (`none`/`anthropic`/`ollama`), con
retrocompatibilidad total: sin `AI_PROVIDER` definida, `ANTHROPIC_API_KEY` sola sigue activando
Anthropic exactamente como en Sprint 33 (ningún despliegue/test existente cambia de
comportamiento). Se añade `ollama` como segundo proveedor real, llamando a un servidor Ollama
local (`http://localhost:11434` por defecto, sin credenciales) — ver `docs/09_AI.md` §"IA local
con Ollama" para instalación/configuración completas.

Esta selección vive enteramente en el proceso Python — Spring Boot nunca conoce ni necesita
conocer qué proveedor está activo; el Gateway (Java) sigue sin credenciales de ningún proveedor,
tal como exige ADR-AI-001. No se introduce ninguna jerarquía Java `AiProvider`/`OllamaAiProvider`
paralela: el `AiProvider` Java (síncrono, D10-2, solo `NoOpAiProvider`) es un caso de uso distinto
(resumen/explicación/redacción de mensaje) que este sprint no toca — la propuesta conceptual del
encargo ("AiProvider ├── NoOpAiProvider ├── OllamaAiProvider └── AnthropicAiProvider") se resuelve
arquitectónicamente en el Worker, donde ya vivía la única selección real de proveedor de
extracción documental desde Sprint 33, en vez de duplicar una jerarquía paralela en Java que no
tendría ningún llamador.

**D34-AI-1.1 — Ollama es solo texto.** Ningún modelo de visión Ollama viene instalado por defecto
(los modelos multimodales ocupan varios GB, explícitamente descartado por el propio encargo). El
proveedor `ollama` solo admite `text/plain`/`text/html`; PDF/imagen con `AI_PROVIDER=ollama`
devuelve `FAILED` honesto ("Unsupported document type for this AI provider"), nunca una
extracción inventada ni un 500. PDF/imagen siguen requiriendo `AI_PROVIDER=anthropic`.

**D34-AI-1.2 — Modelo por defecto.** `llama3.2:1b` (~1.3GB), elegido por ser razonable para
hardware de desarrollo (no el modelo más capaz que Ollama puede ejecutar, el más razonable para
un portátil de desarrollo) — configurable vía `OLLAMA_MODEL`.

**Alternativas consideradas:** una jerarquía Java `AiProvider` paralela con implementaciones
Ollama/Anthropic — descartada porque el `AiProvider` Java existente no interviene en la extracción
documental (ese flujo pasa por `AiTaskDispatcher`/Worker desde D10-4/D10-5) y duplicar la
abstracción allí no tendría ningún efecto real, solo código muerto.

**Consecuencias:** `ai-worker/main.py` reestructurado (`_resolve_provider`, `run_anthropic_extraction`,
`run_ollama_extraction`, `_call_ollama`); 11 tests nuevos en `ai-worker/tests/test_main.py`; sin
cambios en el backend Java, sin migraciones, sin permisos nuevos, sin endpoints nuevos.

**Documentos afectados:** este documento, `docs/09_AI.md`, `.env.example`.

**Estado:** APPROVED. Implementado y validado en Sprint 34.

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

### Adenda Sprint 23 — Persistencia del refresh token en `sessionStorage` para la recuperación de sesión

**Contexto:** la Fase 0 de Sprint 23 (gestión de sesión) confirmó un defecto P0 heredado desde Sprint 13: `SessionService.hydrate()` y `PortalSessionService.hydrate()` estaban definidos pero **nunca se llamaban** — no había `APP_INITIALIZER`/`provideAppInitializer`, y los componentes de login navegaban tras `authService.login()` sin hidratar el `SessionStore`, por lo que `SessionStore.hasPermission()` devolvía siempre `false`, `permissionGuard` redirigía todo a `/forbidden` y `*appHasPermission` ocultaba toda la UI restringida. Además, el punto D2 de esta ADR exigía que los tokens vivieran solo en memoria, lo que impedía que una recarga de página (F5) mantuviera la sesión. La auditoría integral previa clasificó ambos como P0. Se presentó al responsable del proyecto la contradicción entre "recarga mantiene sesión" (requisito del sprint) y "tokens solo en memoria" (esta ADR); **el responsable eligió la opción recomendada**: persistir SOLO el refresh token en `sessionStorage`, manteniendo el access token únicamente en memoria.

**Decisión:**

1. **Solo el refresh token se persiste en `sessionStorage`; el access token sigue viviendo únicamente en memoria (signal).** `AuthService` (`brika.session.refreshToken`) y `PortalAuthService` (`brika.portal.session.refreshToken`, clave físicamente separada — `ADR-PORTAL-AUTH-001`) escriben el refresh token recibido de `login`/`refresh` y lo eliminan en `logout`/`clearSession`/401. `sessionStorage`, no `localStorage`: sobrevive al reload en la misma pestaña pero se descarta al cerrarla, y el token es opaco, rotativo y revocable en servidor (Sprint 22), por lo que el valor almacenado solo es útil hasta su primera rotación o revocación — la superficie de riesgo de XSS, presente en cualquier modelo de tokens en el navegador, no se amplía frente a la alternativa de persistir ambos tokens.
2. **`restore()` recupera la sesión en el arranque.** Ambos servicios de auth ganan `restore(): Promise<boolean>`: leen el refresh token almacenado y, si existe, lo intercambian por `POST /auth/refresh` (el equivalente Portal), aplicando la nueva pareja y reprogramando la rotación; si no hay token o el refresh falla (expirado/revocado), limpian el estado y devuelven `false`.
3. **La hidratación se dispara desde `provideAppInitializer` en `app.config.ts`** (`restoreAndHydrate` en `core/session/session-bootstrap.ts`), de modo que ninguna navegación protegida puede ejecutarse antes de que el estado de sesión esté hidratado — ataca la causa raíz del P0. Por cada superficie: si `restore()` recupera sesión, se llama a `hydrate()` (`/me` + `/me/permissions`, o `/api/v1/portal/me`); si la hidratación falla, el estado de auth y de sesión se derriba junto (`clearSession()` + `clear()`), nunca dejando la app parcialmente autenticada. `SessionService.hydrate()` ya era atómico (fija usuario y permisos juntos, o nada) mediante `Promise.all`.
4. **Login y logout hidratan/limpian el estado de sesión.** `LoginComponent.submit()` y `PortalLoginComponent.submit()` llaman a `hydrate()` tras un login correcto y antes de navegar; si la hidratación falla, muestran el error sin navegar. `UserMenuComponent.logout()`/`PortalShellComponent.logout()` limpian también el `SessionStore`/`PortalSessionStore`, y `error.interceptor` limpia el store correspondiente junto a `clearSession()` en el 401.

**Alternativas consideradas:** persistir ambos tokens en `sessionStorage` (más simple, pero expone el access token a persistencia y dificulta la rotación en memoria) — descartada por esta ADR y la Sprint 22: el access token debe quedar en memoria; no persistir nada y exigir re-login tras cada recarga — descartada porque el requisito del sprint era mantener la sesión tras recarga; usar `localStorage` para el refresh token (mantiene la sesión entre pestañas y reinicios del navegador) — descartada por ampliar innecesariamente la superficie de persistencia y el riesgo de sesión huérfana frente a `sessionStorage`, efímera por pestaña.

**Consecuencias:** la regla de "tokens solo en memoria" de esta ADR queda **superada exclusivamente para el refresh token**, que ahora persiste en `sessionStorage`; el access token continúa solo en memoria. Cualquier sprint futuro que reabra la gestión de sesión debe tratar esta excepción como deliberada y documentada aquí. `25_CLAUDE_CODE_EXECUTION_GUIDE.md` (o el informe de cierre de Sprint 23) recoge el flujo de recarga resultante.

**Documentos afectados:** este documento (adenda a ADR-FRONTEND-001).

**Estado:** APPROVED (implementado en Sprint 23).

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

## ADR-PROCESS-006 — Definición de Sprint 18: Administración (Users/Companies/Plans/Subscriptions)

**Contexto:** al cierre de Sprint 17, el proceso de 3 fases de `ADR-PROCESS-005` se aplicó por segunda vez para definir el siguiente bloque de trabajo. El candidato inicial propuesto era administración (Users/Companies/Plans), ya identificado como bloque independiente pendiente desde `ADR-PROCESS-004` (Sprint 16) y reconfirmado en `ADR-PROCESS-005` (Sprint 17). La Fase 1/Definición exigió, antes de aceptar ese candidato, una auditoría del backend real (controllers, DTOs, permisos sembrados, tests de integración) en lugar de asumir el alcance desde la documentación de especificación.

**Decisión:**

1. **Sprint 18 queda definido oficialmente como: Users + Companies + Plans + Subscriptions (frontend interno), incluyendo explícitamente la gestión de suscripción como sección embebida en el detalle de empresa (no como bloque propio) y excluyendo explícitamente Entitlements por ausencia total de API.** Objetivo: llevar al frontend interno las capacidades de `identity` (`UserController`, `CompanyController`) y `plan` (`PlanController`, `CompanySubscriptionController`) que ya existen y están operativas en el backend desde los Sprints 2 y 12.1 — ningún endpoint, entidad, migración ni permiso nuevo.
2. **Hallazgos de la auditoría de Fase 1 que determinaron el diseño exacto del frontend, verificados leyendo el código fuente y no la documentación:**
   - `UserController` exige `requireTenant()` para **todas** sus operaciones — SUPERADMIN nunca lo resuelve sin `SUPPORT_SESSION` (no implementada), confirmado por el test `superadminWithoutSupportSessionCannotAccessUsersEndpoint`. Es la misma limitación estructural que Tareas/Comunicaciones (Sprint 17), reproducida aquí sin intentar solucionarla.
   - `USER_ASSIGN_ROLE` está sembrado para SUPERADMIN/MANAGER en `V9__seed_role_permissions.sql` pero **no existe ningún endpoint que lo compruebe** en todo el código backend — deuda técnica preexistente, documentada, explícitamente no implementada en este sprint.
   - `UserProvisioningService.validateCompanyAssignment` rechaza incondicionalmente crear un usuario con rol `SUPERADMIN` a través de `POST /api/v1/users` (el `company_id` del creador nunca es `null`) — el selector de rol del formulario de alta se limita a MANAGER/BROKER, no por elección de producto sino porque SUPERADMIN es estructuralmente inalcanzable por esa vía; CLIENT queda fuera por pertenecer al flujo separado de Portal Cliente (`CLAUDE.md` §7).
   - `CompanyController`/`PlanController`/`CompanySubscriptionController` son GLOBAL (sin `requireTenant()`) — SUPERADMIN los usa sin la limitación de `SUPPORT_SESSION` que sí le afecta en Users/Tasks/Communications; `PLAN_READ`/`PLAN_MANAGE`/`SUBSCRIPTION_READ`/`SUBSCRIPTION_MANAGE` no están asignados a MANAGER/BROKER en el seed (SUPERADMIN-only).
   - `EntitlementResolutionService` no tiene ningún controller (confirmado: "not wired to any endpoint" en su propio javadoc) y no existe ningún permiso `ENTITLEMENT_*` en el catálogo sembrado — confirmación mecánica de que no hay nada que exponer en frontend.
3. **Primer caso del proyecto donde una sección embebida en un detalle es genuinamente inalcanzable para parte de los visores con acceso al recurso padre:** MANAGER tiene `COMPANY_READ` sobre su propia empresa pero nunca `SUBSCRIPTION_READ`. A diferencia de todas las secciones embebidas anteriores (`case-detail` desde Sprint 3), donde todo rol con acceso al recurso padre tenía también acceso de lectura a cada sección, aquí la petición de suscripción/planes se dispara solo si la sesión ya tiene `SUBSCRIPTION_READ` — evitando que MANAGER reciba un 403 espurio en el banner de error de una sección que para él está correctamente oculta.
4. **Explícitamente fuera de Sprint 18:** Entitlements (sin API), Portal Cliente (Sprint 19), `SUPPORT_SESSION`, aprovisionamiento Keycloak, corrección de `USER_ASSIGN_ROLE`, cualquier productor de notificaciones/RabbitMQ.

**Alternativas consideradas:** incluir una pantalla de "asignación de rol" ya que el permiso `USER_ASSIGN_ROLE` existe — descartada explícitamente, construirla sin endpoint real habría sido inventar funcionalidad (prohibido por `CLAUDE.md` §3 y por la propia autorización de Fase 2 de este sprint). Ofrecer SUPERADMIN como opción en el alta de usuario — descartada, el backend la rechaza siempre. Incluir Entitlements con datos de solo lectura desde `EntitlementResolutionService` mediante un endpoint nuevo — descartada, viola la prohibición explícita de no crear endpoints nuevos.

**Consecuencias:** `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §3 pasa a incluir Sprint 18; `09_ROADMAP.md` Fase L se actualiza para reflejar que Users/Companies/Plans ya no es un bloque pendiente sin fecha — Portal Cliente (Sprint 19) queda como el único bloque de Fase L aún no iniciado.

**Documentos afectados:** este documento, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `09_ROADMAP.md`.

**Estado:** APPROVED.

## ADR-PORTAL-AUTH-001 — Frontera de seguridad Portal Cliente (formalización retroactiva)

**Contexto:** este identificador se ha citado desde Sprint 13 (`ADR-FRONTEND-001`, D2) y Sprint 16/17 (`ADR-PROCESS-004`, `ADR-PROCESS-005`) para justificar por qué Portal Cliente queda siempre fuera del alcance de cada sprint del frontend interno, pero nunca tuvo su propia entrada dedicada en este documento — la decisión arquitectónica real se tomó y se implementó íntegramente en Sprint 7 (backend), sin que el ADR correspondiente se redactara en su momento. Se formaliza aquí retroactivamente, sin reescribir su historia, siguiendo el mismo precedente que `ADR-PROCESS-004` estableció para los Sprints 13-15.

**Decisión:** Portal Cliente es una frontera de seguridad completa e independiente respecto al frontend/backend interno (`CLAUDE.md` §7 — "CLIENT is a separate security boundary. Default visibility is private."), implementada desde Sprint 7 mediante:

1. **Dos `SecurityFilterChain` completamente independientes**, ordenados explícitamente: `@Order(1)` valida únicamente `/api/v1/portal/**` contra el realm Keycloak `brika-portal` (issuer propio, `PortalJwtDecoder` dedicado — deliberadamente no implementa `JwtDecoder` para no ser candidato ambiguo de inyección en la cadena interna); `@Order(2)` valida todo lo demás contra el realm `brika`, vía `BrikaJwtAuthenticationConverter`. Ninguna petición puede autenticarse contra ambas cadenas a la vez.
2. **Un principal Portal (`ClientPortalAccount` / `PortalAuthenticationToken`) que nunca resuelve a un `User` interno.** `PortalAuthorizationService` no toca `UserRepository` ni `PermissionResolutionService` (el servicio de resolución de permisos del frontend interno) en ningún punto de su implementación — estructuralmente imposible, no solo por convención.
3. **`PortalPermissionResolutionService` devuelve siempre el conjunto fijo de 11 permisos `CLIENT`** (`role_permissions` donde `role.code = 'CLIENT'`), idéntico para cualquier cuenta Portal autenticada — no existe variación por cuenta, a diferencia del frontend interno donde el conjunto de permisos varía por usuario/rol/empresa.
4. **`PortalCaseAccessService` (contraparte de `CaseAccessService`) usa `case_clients` (participación del cliente) en vez de `case_assignments` (asignación de broker), y enmascara como 404 (`CASE_NOT_FOUND`) tanto un caso de otro tenant como un caso del mismo tenant en el que el cliente no participa** — más estricto que el modelo interno, donde un MANAGER puede al menos saber que existe un caso de otro broker dentro de su propia empresa.
5. **Ningún endpoint Portal puede aprovisionarse desde el propio Portal.** La vinculación cliente↔identidad Keycloak (`POST /api/v1/clients/{clientId}/portal-account`) vive exclusivamente en el lado interno (permiso `CLIENT_PORTAL_ACCOUNT_CREATE`, Sprint 7) y nunca autoprovisiona el usuario en Keycloak — el MANAGER debe conocer de antemano un `externalIdentityId` real ya existente en el realm `brika-portal`, exactamente el mismo patrón que Users (Sprint 2/18).

**Alternativas consideradas:** un único `SecurityFilterChain` con un claim de tipo diferenciando token interno/Portal — descartada, un fallo de configuración podría filtrar permisos internos a un token Portal o viceversa; la separación física de cadenas hace ese fallo estructuralmente imposible en vez de depender de que la lógica de un claim se implemente correctamente. Reutilizar `PermissionResolutionService` con un "rol especial CLIENT" — descartada por la misma razón que el punto 2 de la decisión: un bug en la resolución de permisos internos no debe poder afectar nunca al Portal.

**Consecuencias:** todo sprint que toque Portal Cliente (Sprint 19 en adelante) hereda esta frontera sin poder debilitarla; cualquier intento de compartir código de autenticación/autorización entre frontend interno y Portal requiere una excepción explícita y justificada, no un valor por defecto.

**Documentos afectados:** este documento (formalización retroactiva; sin cambios de código).

**Estado:** APPROVED (implementado desde Sprint 7; formalizado en este documento en Sprint 19).

## ADR-PROCESS-007 — Definición, ejecución y cierre de Sprint 19: Portal Cliente

**Contexto:** al cierre de Sprint 18, Portal Cliente quedaba como el único bloque de Fase L sin iniciar (`ADR-PROCESS-006`), ya comprometido a Sprint 19 desde `frontend/src/app/auth/README.md` (Sprint 13) y reconfirmado en `ADR-PROCESS-004`/`ADR-PROCESS-005`. La Fase 1/Definición de Sprint 19 exigió, de forma explícita y más extensa que en sprints anteriores, una auditoría completa del estado real (backend, frontend, configuración Keycloak, modelo de roles/permisos CLIENT, endpoints Portal existentes/faltantes, tests, documentación) verificada contra el código fuente, sin asumir nada de la documentación de especificación — dado que Portal Cliente es la primera frontera de seguridad completa que el frontend debe respetar (`ADR-PORTAL-AUTH-001`).

**Decisión:**

1. **Sprint 19 queda definido oficialmente como: Portal Cliente completo** — realm Keycloak `brika-portal` provisionado, `PortalAuthService`/`PortalSessionStore` independientes (Authorization Code + PKCE contra el realm Portal, nunca el interno), Dashboard, listado y detalle de operación, documentos publicados, solicitudes de documentación, subida de documentos, mensajería CLIENT con adjuntos, notificaciones con marcado de leída, perfil, y separación absoluta entre sesión Portal y sesión interna (interceptores HTTP particionados por URL, sin solapamiento).
2. **Hallazgos de la auditoría de Fase 1 que determinaron el alcance exacto, verificados leyendo el código fuente:** el backend Portal (Sprint 7) tenía 11 endpoints reales y probados, pero con dos huecos funcionales identificados por primera vez en esta auditoría: `PortalNotificationController` no tenía ninguna capacidad de escritura (`markRead`) — el cliente podía leer notificaciones pero nunca marcarlas como leídas — y `PortalDocumentController` no exponía ninguna vista explícita de "solicitudes de documentación pendientes" (solo existía la heurística de marcar como cumplida al detectar una subida coincidente, sin ninguna forma de que el cliente viera qué se le pedía antes de subir nada).
3. **Excepción explícita, acotada y expresamente autorizada a la regla general de "no modificar backend en un sprint de frontend"**, limitada exactamente a los dos huecos del punto 2: se añade `PATCH /api/v1/portal/notifications/{id}/read` (reutilizando el permiso ya sembrado `PORTAL_NOTIFICATION_READ`, sin inventar uno nuevo) y `GET /api/v1/portal/cases/{id}/document-requests` (usando el permiso ya sembrado pero hasta ahora huérfano `PORTAL_DOCUMENT_REQUEST_RESPOND`) — ambos construidos siguiendo estrictamente el patrón arquitectónico ya establecido en `ADR-PORTAL-AUTH-001` (autorización exclusiva del propio `ClientPortalAccount`, aislamiento por cliente y por caso verificado en tests de integración dedicados). Ningún otro endpoint, tabla ni permiso se crea fuera de estos dos puntos.
4. **La heurística existente de "responder solicitudes" (marcar como cumplida al detectar una subida coincidente) se mantiene exactamente igual, sin sustituirla** — la nueva vista explícita del punto 3 es un añadido puramente de lectura sobre el estado ya calculado por esa heurística, no un mecanismo alternativo.
5. **`PortalDocumentRequestResponse` expone `documentTypeId`, `documentTypeCode` y `documentTypeName` juntos** — el token Portal no puede llamar a `GET /api/v1/document-types` (fuera del matcher `/api/v1/portal/**`, validado solo contra el realm interno), así que el nombre resuelto debe embeberse aquí o el cliente vería un UUID desnudo; `documentTypeId` se mantiene porque la acción "Subir documento" de una solicitud concreta lo necesita para llamar a `POST /api/v1/portal/cases/{id}/documents` — es una clave foránea a un catálogo global no perteneciente a ningún tenant, por lo que exponerla no supone ningún riesgo de aislamiento.
6. **Corrección de un defecto de cableado descubierto durante el cierre**: `portalAuthInterceptor` se había implementado y probado unitariamente, pero nunca se registró en `provideHttpClient(withInterceptors([...]))` de `app.config.ts` — el token Portal nunca se adjuntaba a ninguna petición real de la aplicación, aunque cada test unitario aislado pasaba correctamente. Detectado únicamente durante la validación E2E real contra Keycloak/backend (no por ningún test unitario, que no ejercita el árbol de providers de la aplicación), y corregido antes del cierre.

**Alternativas consideradas:** implementar el marcado de notificaciones reutilizando el endpoint interno `PATCH /api/v1/notifications/{id}/read` desde el Portal — descartada explícitamente por instrucción directa del responsable del proyecto ("No reutilizar el endpoint interno si su seguridad o SecurityFilterChain no corresponde al Portal"), consistente con `ADR-PORTAL-AUTH-001`. Sustituir la heurística de auto-cumplimiento por la nueva vista explícita — descartada explícitamente por instrucción directa ("Queremos una vista explícita y real, no sustituir la heurística existente"). Omitir `documentTypeId` de la nueva vista por ser "un detalle interno" — descartada al comprobar que el frontend no tiene ninguna otra forma de obtenerlo para poder subir el documento solicitado.

**Consecuencias:** Portal Cliente queda cerrado como bloque completo; Fase L de `09_ROADMAP.md` queda sin bloques pendientes de Sprint 13-19. Cualquier futuro cambio a Portal Cliente hereda `ADR-PORTAL-AUTH-001` y el patrón de excepción acotada de este ADR (nunca una autorización genérica para tocar backend desde un sprint de frontend).

**Documentos afectados:** este documento, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `09_ROADMAP.md`.

**Estado:** APPROVED.

## ADR-PROCESS-008 — Sprint 20: rebranding Brikka, imagen de marca, normalización de textos y catálogos "Tipo"

**Contexto:** al cierre de Sprint 19, Fase L de `09_ROADMAP.md` quedó sin bloques pendientes. El responsable del proyecto solicitó, antes de iniciar la auditoría general prevista como siguiente paso, un sprint de rebranding (Brika → Brikka), imagen de marca, normalización de textos en español y revisión de campos "Tipo" implementados como texto libre — explícitamente fuera del proceso de 3 fases habitual en cuanto a que la Fase 1/Definición ya venía dada en el propio encargo, sin necesidad de una fase de descubrimiento adicional salvo un punto: la conversión de los campos "Tipo" a desplegable.

**Decisión:**

1. **Marca visible del producto: Brika → Brikka**, en los 5 puntos donde aparecía en la aplicación (título del navegador `index.html`, tarjeta de login interno, tarjeta de login Portal, cabecera de navegación interna, cabecera de navegación Portal). Identificadores técnicos internos (paquetes Java `com.brika.platform.*`, nombre de base de datos, client IDs y nombres de realm de Keycloak `brika`/`brika-portal`, comentarios de código que documentan el contrato interno de la API, nombres de fixtures de test como `tradeName: 'Brika'` en `company.service.spec.ts`) **deliberadamente no renombrados** — no son visibles al usuario final y renombrarlos no aporta valor, solo riesgo de romper configuración externa (Keycloak) o crear un diff enorme sin beneficio.
2. **Imagen de marca analizada y documentada, explícitamente no aprobada por este acto**: `docs/branding/BRIKKA_BRAND_GUIDELINES.md` y `docs/branding/BRIKKA_BRAND_REVIEW.md` documentan la identidad construida a partir de la única referencia visual existente en el repositorio (`docs/branding/file_0000000080d08243a1a6c92157dc0259.png`, un póster de lanzamiento rasterizado). Colores extraídos por muestreo real de píxeles vía `canvas.getImageData` (no estimación visual) con confianza alta para azul de marca/navy/blanco, confianza baja para verde/ámbar de estado (muestras pequeñas) y sin extracción fiable para el rojo de estado (propuesto, no extraído). El trazado vectorial del símbolo y la familia tipográfica del wordmark son inferencia de diseño, no extracción, al no existir ningún archivo vectorial ni con metadatos de fuente en el material de partida — documentado explícitamente como tal. La identidad queda ANALIZADA → DOCUMENTADA → **PENDIENTE DE APROBACIÓN**, tal y como exigió expresamente el encargo.
3. **Activos de logotipo generados en `docs/branding/assets/`**: 7 variantes SVG (primario, oscuro, claro, monocromo, vertical, símbolo, favicon) más una exportación PNG de 512×512 del favicon. No existía ninguna herramienta de rasterización de imágenes en el entorno de ejecución (`rsvg-convert`, `inkscape`, `cairosvg`, `imagemagick` — todas ausentes); el PNG real se generó usando el navegador disponible (`canvas.drawImage` + `toDataURL('image/png')`), documentado como limitación técnica en `BRIKKA_BRAND_REVIEW.md`. El favicon de la aplicación (`frontend/public/favicon.svg`, con `.ico` de 32×32 como *fallback* para navegadores sin soporte de favicon SVG) se sustituyó a partir de estos activos.
4. **Retemado visual completo (paleta Material, tipografía de interfaz) explícitamente NO ejecutado en este sprint** — la aplicación sigue usando `mat.$azure-palette` (ya razonablemente próxima al azul de marca extraído) y Roboto. Cambiar esto es un cambio de superficie mucho mayor que "sustituir la marca visible" y queda documentado como trabajo de seguimiento pendiente de aprobación, no como parte de este cierre.
5. **Auditoría y normalización de textos visibles**: la infraestructura de traducción ya existente (`status-labels.ts`/`StatusLabelPipe`/`friendlyErrorMessage`, establecida en la Auditoría UX/i18n pre-Sprint 16) ya cubría la inmensa mayoría de la aplicación, incluido Portal Cliente (Sprint 19) y administración (Sprint 18) — verificado explícitamente, no asumido. Los huecos reales encontrados y corregidos: filtraciones de código técnico sin traducir en `case-detail`, `case-list`, `portal-case-detail` y `portal-dashboard` (los 4 campos "Tipo" recatalogados en el punto 6, cuyas pantallas de visualización aún no usaban las nuevas etiquetas). Ningún texto en inglés visible se encontró en el resto de la aplicación.
6. **4 campos "Tipo" convertidos de texto libre a desplegable con catálogo cerrado**: tipo de operación (`OPERATION_TYPES`), tipo de inmueble (`PROPERTY_TYPES`), tipo de asignación (`ASSIGNMENT_TYPES`), tipo de tarea (`TASK_TYPES`) — todos en sus respectivos `*.model.ts`, con etiquetas en español en `status-labels.ts`. **Hallazgo crítico de la investigación previa a implementar este punto, verificado leyendo el código fuente y no asumido de la documentación**: ninguno de los 4 campos tenía un catálogo cerrado real en ningún punto del proyecto — los tres primeros (`operationType`, `assignmentType`, `propertyType`) llevaban comentarios explícitos desde el Sprint 3/14 ("free text, no catalog is documented anywhere"), el cuarto (`type` de tarea) desde el Sprint 17 ("free text... rather than an invented catalog"); ninguna migración tiene `CHECK` constraint sobre estas columnas; en toda la base de datos de desarrollo solo existían los valores `MORTGAGE` (operación), `FLAT` (inmueble), `PRIMARY` (asignación) y `DOCUMENT_REVIEW`/`CALL` (tarea) — datos de ejemplo, no evidencia de un enum. Presentado este hallazgo al responsable del proyecto como una contradicción real con el contrato existente (regla de parada del propio proceso), se solicitó y obtuvo **autorización explícita para definir un catálogo de negocio nuevo** — los valores de cada catálogo (ver `status-labels.ts` para las 4 listas completas) son una decisión de producto tomada en este sprint, no una extracción de ningún enum/constante preexistente, y se documentan como tal para que nunca se presenten como si ya existieran.
7. **Migración `V16__normalize_operation_type_seed_data.sql`**: corrige el único valor de `operation_type` existente en toda la base de datos de desarrollo (`MORTGAGE`) a `PURCHASE`, el valor del nuevo catálogo más próximo en la práctica — sin cambio de esquema, sin `CHECK` constraint añadido (el backend sigue aceptando cualquier texto en esa columna; solo el frontend impone el catálogo cerrado). Es el único cambio de backend de todo el sprint, autorizado explícitamente junto con el punto 6.
8. **Auditoría general explícitamente NO iniciada en este sprint** — es el bloque siguiente (Sprint 21 en adelante), tal y como exigió el encargo ("No iniciar ningún trabajo de auditoría general").

**Hallazgos encontrados pero explícitamente no corregidos, con razón documentada (no inventar catálogos sin justificación):**
- `notification.type` (lista de notificaciones interna y Portal): sin catálogo cerrado documentado, y sin ningún productor de notificaciones real conectado todavía (hecho ya establecido desde Sprint 17) — inventar una traducción ahora sería especulativo sobre un catálogo que ni siquiera se ha confirmado que coincida con los nombres de evento de `20_RABBITMQ_SPECIFICATION.md`.
- `plan.status` (catálogo de Planes, Sprint 18): texto libre por diseño explícito del propio `Plan.java` ("Global catalog, not tenant-owned", sin `CHECK` constraint), mismo patrón que los 4 campos del punto 6 antes de esta decisión — no se extiende la autorización de este sprint a un campo no incluido en el encargo.
- `BankMatchRuleResult.field`/`.operator` (detalle de resultado de matching bancario): claves técnicas del motor de reglas, sin catálogo documentado (criterios bancarios son JSON schemaless), visibles únicamente a broker/manager que ya trabajan con el JSON de criterios sin traducir en la misma pantalla — no es una regresión de este sprint, es una decisión de diseño previa fuera de alcance.

**Alternativas consideradas:** dejar los 4 campos "Tipo" como texto libre y solo traducir la UI circundante — descartada, el responsable del proyecto autorizó explícitamente definir catálogos nuevos tras presentársele el hallazgo con datos reales de la base de datos. Añadir `CHECK` constraints en la migración V16 para los 4 catálogos — descartada, excede "no modificar backend salvo imprescindible": el frontend ya impone el catálogo cerrado, forzar además la restricción en base de datos no era necesario para cumplir el alcance del sprint. Retemar la paleta Material completa a `#328CFA` ahora que se ha extraído del póster — descartada, es un cambio visual de alcance mucho mayor al de este sprint, queda documentado como pendiente de aprobación en `BRIKKA_BRAND_GUIDELINES.md`.

**Consecuencias:** Fase M de `09_ROADMAP.md` (Rebranding Brikka + normalización UX) queda cerrada. El proyecto no tiene ningún bloque de frontend pendiente sin fecha; el siguiente bloque es la auditoría general (Sprint 21 en adelante), explícitamente no iniciada por este ADR. Cualquier sprint futuro que quiera ampliar los catálogos "Tipo" definidos aquí, o aprobar/modificar la imagen de marca documentada en `BRIKKA_BRAND_GUIDELINES.md`, debe hacerlo mediante una decisión explícita igual de documentada que esta, nunca asumiendo que los valores actuales son definitivos solo por estar en el código.

**Documentos afectados:** este documento, `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `09_ROADMAP.md`, `docs/branding/BRIKKA_BRAND_GUIDELINES.md` (nuevo), `docs/branding/BRIKKA_BRAND_REVIEW.md` (nuevo).

**Estado:** APPROVED.

## ADR-AUTH-001 — Autenticación propia de Brika (Fases 1-6, Keycloak permanece como rollback)

**Contexto:** al cierre de Sprint 21 se ejecutó una fase de "solo análisis" cuyo entregable es `27_KEYCLOAK_REMOVAL_ANALYSIS.md` — auditoría del modelo de autenticación basado en Keycloak vigente desde Sprint 2/7 (dos realms, `brika` para el frontend interno y `brika-portal` para Portal Cliente, formalizados en `ADR-PORTAL-AUTH-001`) y evaluación de alternativas para reducir la dependencia de Keycloak, sin ejecutar ningún cambio de código en esa fase. El documento recomienda "Opción A": sustituir a Keycloak como emisor de tokens, tanto para el login interno (SUPERADMIN/MANAGER/BROKER) como para Portal Cliente, por un emisor de JWT propio y stateless construido en el propio backend de Brika, mantener Keycloak instalado y disponible como mecanismo de rollback mientras la nueva implementación se construye y valida, y no migrar usuarios reales hasta una fase posterior explícita. El responsable del proyecto autorizó por escrito la implementación de la Opción A siguiendo el plan de fases de ese documento; los límites concretos de esa autorización quedan citados literalmente, por número de sección, en los propios artefactos que esta ADR documenta — `V17__self_auth_credentials.sql` cita §8, §10, §11 y §16; `SecurityConfig.java` y `application.yml` citan §10; `UserAccessTokenIssuer.java`/`UserAuthenticationService.java` citan §2, §11 y §12; el frontend (`auth.service.ts`, `environment.ts`) cita el documento y la "Opción A" explícitamente. Esta ADR formaliza en este registro de decisiones el resultado de implementar las Fases 1-6 de ese plan durante Sprint 22.

**Decisión:**

1. **Se construye un emisor de JWT propio de Brika, completo y probado, como alternativa íntegra a los tokens emitidos por Keycloak, para los dos dominios de autenticación existentes (interno y Portal Cliente), duplicando deliberadamente la implementación entre ambos sin compartir código** — el mismo patrón de separación física que `ADR-PORTAL-AUTH-001` ya exige entre `SecurityFilterChain`s se extiende ahora a la capa de emisión y validación de tokens: `PortalAuthenticationService` es una copia funcional independiente de `UserAuthenticationService`, nunca una capa compartida con un parámetro de "modo". La infraestructura añadida es:
   - **Esquema (`V17__self_auth_credentials.sql`)**: `user_credentials` y `portal_account_credentials` (hash Argon2id de contraseña, una fila por `users.id`/`client_portal_accounts.id`); `user_refresh_tokens` y `portal_refresh_tokens` (tokens opacos, solo se persiste el hash SHA-256 vía `TokenHasher`, con `family_id` para agrupar la cadena de rotación y `replaced_by_token_id` para detectar reutilización de un token ya rotado — reutilizar un token usado revoca toda la familia); `user_password_reset_tokens` y `portal_password_reset_tokens` (token opaco de un solo uso, expiración corta, `used_at` para invalidación); `login_attempts` (contador de intentos fallidos por `realm`+`identifier`, ventana basada en `attempted_at`, sin infraestructura nueva tipo Redis, coherente con la restricción de la autorización de no introducir infraestructura adicional).
   - **Contraseñas**: `PasswordEncoderConfig` (Argon2id vía `Argon2PasswordEncoder` de `spring-security-crypto`), que exige añadir `org.bouncycastle:bcprov-jdk18on:1.78.1` a `backend/pom.xml` — el JDK no trae implementación Argon2 propia; es la dependencia que la propia documentación de Spring Security nombra para soporte Argon2id, no un framework arbitrario.
   - **Claves y tokens (`com.brika.platform.security`)**: `SelfIssuedTokenKeys` genera/carga dos parejas RSA-2048 completamente independientes (interna y Portal) desde `brika.security.self-auth.internal-signing-key-pem`/`...portal-signing-key-pem` (PKCS8 DER en base64) o, si no están configuradas, genera un par efímero por proceso con un `log.warn` explícito de que los tokens dejan de validar tras un reinicio — válido solo para desarrollo local. `SelfIssuedJwtConfig` expone los `JwtEncoder`/`JwtDecoder` de cada realm con issuers distintos y no resolubles por red (`https://auth.brika.internal/self/internal` y `.../self/portal`, key id `brika-internal-1`/`brika-portal-1`), validados con `JwtValidators.createDefaultWithIssuer(...)`. `OpaqueTokenGenerator` genera los valores de refresh/reset token en claro que solo el cliente conserva (el backend solo persiste su hash).
   - **Orquestación (`com.brika.platform.auth`, paquete nuevo completo)**: `UserAuthenticationService`/`PortalAuthenticationService` implementan login, refresh (rotación con `UserRefreshTokenService`/`PortalRefreshTokenService`), logout (revocación), cambio de contraseña (que además invalida todos los refresh tokens vigentes del usuario, autorización §12) y el ciclo de recuperación de contraseña (`requestPasswordReset`/`confirmPasswordReset`). `LoginAttemptService` aplica el bloqueo temporal tras repetidos fallos (`TooManyLoginAttemptsException`) leyendo `login_attempts`. `UserAccessTokenIssuer`/`PortalAccessTokenIssuer` emiten el JWT de acceso (~15 minutos, autorización §2) firmado con la clave del realm correspondiente. `UserAuthController`/`PortalAuthController` (subpaquete `web`) exponen `POST /api/v1/auth/{login,refresh,logout}` y `POST /api/v1/auth/password-reset/{request,confirm}`, con el equivalente exacto bajo `/api/v1/portal/auth/...` — añadidos como nuevos matchers `permitAll()` dentro de las dos `SecurityFilterChain` ya existentes en `SecurityConfig` (`@Order(1)` Portal, `@Order(2)` interna), sin crear una tercera cadena ni tocar el resto de su configuración.
   - **Recuperación de contraseña sin proveedor de email real**: `LoggingPasswordResetNotifier` (implementación de `PasswordResetNotifier`) registra el token en el log del backend en vez de enviarlo por correo — deliberadamente no conectado a ningún proveedor real, porque la autorización prohíbe explícitamente elegir/contratar uno sin autorización adicional (§8). `requestPasswordReset` siempre responde con éxito exista o no el email (autorización §11, "no revelar si el usuario existe"), y `UserAuthenticationService.login`/`PortalAuthenticationService` aplican la misma indistinguibilidad entre "usuario no existe", "usuario deshabilitado" y "contraseña incorrecta": en los tres casos se ejecuta igualmente `credentialService.verify(...)` sobre un UUID aleatorio antes de fallar, para que ni el resultado ni el tiempo de respuesta filtren cuál de los tres casos ocurrió.
   - **Interruptor único de activación**: `SecurityConfig` recibe `brika.security.self-auth.enabled` (`${SELF_AUTH_ENABLED:false}`, por defecto `false`) y lo usa dentro de los beans `jwtDecoder()`/`portalJwtDecoder()` para decidir, por cada una de las dos `SecurityFilterChain` ya existentes, si el decodificador activo es el `NimbusJwtDecoder` autoemitido o el `LazyIssuerJwtDecoder` respaldado por Keycloak que ya existía. No se construye confianza simultánea en ambos emisores sobre la misma cadena (opción de mayor alcance, descartada — ver Alternativas). Volver a `false` restaura exactamente el comportamiento anterior sin cambio de código: es, literalmente, el procedimiento de rollback exigido por la autorización §10.

2. **Decisión de mapeo de identidad (confirmada explícitamente por el responsable del proyecto entre 3 opciones presentadas): el `sub` del JWT autoemitido reutiliza el valor ya existente en `users.external_identity_id` / la columna equivalente de `client_portal_accounts`** — el mismo campo que hoy contiene el subject de Keycloak. `UserAccessTokenIssuer.issue(String subject)` recibe ese valor tal cual, y `BrikaJwtAuthenticationConverter`/`PortalJwtAuthenticationConverter` — que resuelven el usuario local vía `findByExternalIdentityId(jwt.getSubject())` — no requieren ningún cambio para aceptar estos tokens el día que la Fase 2/3 los conecte de verdad a `SecurityConfig`. Las dos alternativas descartadas en el momento de decidir fueron: (a) usar el id interno de base de datos como `sub`, que habría exigido modificar ambos converters y el contrato de claims ya validado por los tests existentes; (b) introducir una columna nueva y dedicada para el subject autoemitido, separada de `external_identity_id`, que habría requerido una migración de esquema adicional sobre `users`/`client_portal_accounts` sin necesidad funcional real. La opción elegida es la única de las tres con impacto cero en el esquema y en la capa de conversión ya probada.

3. **Decisión sobre colisión de email entre empresas al hacer login (confirmada explícitamente por el responsable del proyecto entre 3 opciones presentadas): se rechaza como fallo de autenticación genérico, indistinguible de una contraseña incorrecta.** `users.email` es único solo por empresa (`uq_users_company_email`, `ADR-IDENTITY-001`), no globalmente, por lo que una búsqueda de login por email puede en teoría devolver más de una fila entre empresas distintas; `UserAuthenticationService.login` exige `matches.size() == 1` y estado `ACTIVE`, y trata "cero coincidencias", "más de una coincidencia" y "coincidencia deshabilitada" exactamente igual — un fallo genérico, incluyendo tiempo de respuesta equivalente mediante la llamada de verificación sobre un UUID aleatorio ya descrita en el punto 1. La misma política se extiende, por consistencia, al login de Portal Cliente en `PortalAuthenticationService`, pese a que `clients.email` no tiene ninguna restricción de unicidad en absoluto (a diferencia de `users.email`, que sí la tiene por empresa) — el caso de colisión es, si acaso, más probable en Portal, no menos. Las dos alternativas descartadas fueron: (a) cambiar `uq_users_company_email` por una restricción de unicidad global sobre `email`, descartada por debilitar el modelo de tenant ya aprobado en `ADR-IDENTITY-001` sin necesidad; (b) añadir un campo de identificador de empresa al formulario de login para desambiguar, descartada por introducir fricción de UX y una nueva superficie a validar/atacar (enumeración de empresas) para un caso hoy inexistente en los datos reales.

4. **Interno y Portal usan parejas RSA e issuers completamente independientes** (`https://auth.brika.internal/self/internal` frente a `.../self/portal`, claves generadas/cargadas por separado en `SelfIssuedTokenKeys`), verificado con tests dedicados de rechazo cruzado: `SelfIssuedJwtRoundTripTest.internalTokenIsRejectedByThePortalDecoder()` / `.portalTokenIsRejectedByTheInternalDecoder()`, y a nivel de integración HTTP completa `SelfIssuedAuthEndToEndIT.selfIssuedInternalTokenIsRejectedByThePortalChain()` / `.selfIssuedPortalAccessTokenIsAcceptedByAProtectedPortalEndpointAndRejectedInternally()` — un token válido para un realm nunca autentica en el otro, igual que ya ocurre hoy entre los dos realms de Keycloak.

5. **Frontend reescrito para sustituir el flujo Authorization Code + PKCE contra Keycloak por email+contraseña contra el emisor propio**: `frontend/src/app/auth/auth.service.ts` y `frontend/src/app/portal-auth/portal-auth.service.ts` quedan reescritos como formularios reales (`login(email, password)`) contra `POST {apiBaseUrl}/api/v1/auth/login` y el equivalente Portal, manteniendo sin cambios la regla ya vigente de `ADR-FRONTEND-001` de no persistir tokens en `localStorage`/`sessionStorage` (siguen solo en memoria, vía signal). Se añaden `frontend/src/app/auth/password-reset/` y `frontend/src/app/portal-auth/password-reset/` (componentes nuevos) y las rutas `/password-reset` y `/portal/password-reset` en `app.routes.ts`; se eliminan como código muerto `frontend/src/app/auth/pkce.ts` (+`pkce.spec.ts`), `oidc.model.ts` y los directorios `frontend/src/app/auth/callback/` y `frontend/src/app/portal-auth/callback/` completos, junto con las rutas `/auth/callback` y `/portal/auth/callback` que ya no tienen redirect que atender. `frontend/src/environments/environment.ts` pierde los bloques `oidc`/`portalOidc` y queda solo con `apiBaseUrl`. `error-messages.ts` añade los códigos `UNAUTHENTICATED`/`TOO_MANY_ATTEMPTS`, y `api-error.ts`/`error.interceptor.ts` se refactorizan alrededor de un helper `toApiError()` común para que login/refresh/logout (marcados `SKIP_AUTH`) devuelvan un `ApiError` normal en vez de disparar la redirección a `/login` que sí corresponde a una sesión ya viva que expira.

6. **No se migra ningún usuario real y Keycloak no se toca en este sprint.** No se ejecuta ningún script de migración de credenciales, no se modifican los contenedores, ficheros de realm ni el servicio de `docker-compose` de Keycloak, y `brika.security.self-auth.enabled` permanece en `false` por defecto en todos los entornos — el emisor activo hoy, en cualquier entorno donde no se haya fijado explícitamente `SELF_AUTH_ENABLED=true`, sigue siendo Keycloak exactamente como antes de este sprint. La eliminación efectiva de Keycloak, la migración de usuarios reales y la elección de un proveedor de email para recuperación de contraseña quedan explícitamente fuera de esta ADR, como fases posteriores del plan de `27_KEYCLOAK_REMOVAL_ANALYSIS.md` pendientes de autorización propia.

7. **Regresión completa validada antes de dar por cerrada la implementación** (condición para documentar per `CLAUDE.md` §12 / autorización §14): suite backend completa (`mvn verify`, 278 tests preexistentes más los nuevos — `SelfIssuedJwtRoundTripTest`, `SelfAuthFoundationsIT`, `UserAuthEndpointsIT`, `SelfIssuedAuthEndToEndIT`, `PasswordResetEndpointsIT`) y suite frontend completa (`ng test`, 397 tests en 86 ficheros) en verde.

**Alternativas consideradas:** construir confianza simultánea en Keycloak y en el emisor propio dentro de las mismas `SecurityFilterChain` (doble `JwtDecoder`/emisor de confianza combinado) — descartada frente al interruptor único `brika.security.self-auth.enabled`: habría sido un cambio materialmente mayor y más arriesgado sobre `SecurityConfig`, y no aporta nada que el interruptor no dé ya, dado que la autorización solo exige que Keycloak esté *disponible* como rollback, no que ambos emisores convivan validando tráfico real a la vez. Las alternativas específicas de los puntos 2 y 3 (mapeo de identidad y colisión de email) están descritas dentro de cada punto de la Decisión, por haber sido presentadas y resueltas como parte del mismo intercambio de confirmación explícita, no como una discusión separada posterior.

**Consecuencias:** Keycloak sigue siendo una dependencia de despliegue obligatoria (ambos realms, `brika` y `brika-portal`) mientras `brika.security.self-auth.enabled` permanezca en `false`; ningún usuario real ni flujo de Portal Cliente en producción/staging cambia de comportamiento por este sprint hasta que ese flag se active explícitamente entorno por entorno. Activar el flag en un entorno con usuarios reales sin haber completado antes la migración de credenciales (fuera de alcance aquí) dejaría a esos usuarios sin contraseña utilizable — cualquier sprint futuro que active `SELF_AUTH_ENABLED` en un entorno no efímero debe ejecutar antes, como decisión propia y documentada, la fase de migración de usuarios que esta ADR explícitamente no cubre. `19_IDENTITY_OAUTH_SPECIFICATION.md` sigue describiendo únicamente el modelo Keycloak vigente hoy en todos los entornos reales y no se ha actualizado en este sprint; queda pendiente de revisión cuando una fase posterior active el emisor propio fuera de desarrollo local. Cualquier sprint que retome `27_KEYCLOAK_REMOVAL_ANALYSIS.md` para ejecutar sus fases siguientes (migración de usuarios, retirada efectiva de Keycloak, selección de proveedor de email) hereda íntegramente las dos decisiones de los puntos 2 y 3 de este ADR salvo revisión explícita igual de documentada que esta.

**Documentos afectados:** este documento; `27_KEYCLOAK_REMOVAL_ANALYSIS.md` (fases 1-6 de su plan quedan implementadas por esta ADR); `.env.example` (nuevas variables `SELF_AUTH_*`); `GETTING_STARTED.md` (sección de login del frontend reescrita: ya no redirige a Keycloak, y se documenta explícitamente que `SELF_AUTH_ENABLED=true` es necesario para que el nuevo formulario funcione de extremo a extremo en local, junto con la ausencia actual de un endpoint de administración para fijar la contraseña inicial de un usuario existente); `19_IDENTITY_OAUTH_SPECIFICATION.md` (no modificado en este sprint — sigue describiendo el modelo Keycloak vigente; pendiente de actualización cuando una fase posterior active el emisor propio fuera de desarrollo local).

**Estado:** APPROVED (implementado en Sprint 22).

### Adenda Sprint 22 cierre — Retirada efectiva de Keycloak y cierre formal

Esta adenda documenta la **fase de cierre** de Sprint 22, posterior a la implementación de los
puntos 1-6 de esta ADR: la ejecución de las fases 7 y 8 del plan de
`27_KEYCLOAK_REMOVAL_ANALYSIS.md` (retirada efectiva de Keycloak) y la verificación final de la
matriz de usuarios, con la autorización explícita del responsable del proyecto para hacerlo. El
punto 6 de esta ADR (Keycloak permanece como rollback) queda **superado por esta adenda**.

- **Keycloak retirado por completo del entorno local.** El servicio `identity` y su volumen han
  sido eliminados de `docker-compose.yml` (que ahora vive en `docs/docker-compose.yml`); el
  contenedor ya no se levanta. Los realm exports y el tema de login quedan archivados en
  `archive/keycloak-retired-sprint22/` (no eliminados, `CLAUDE.md §3`).
- **El interruptor `brika.security.self-auth.enabled` / `SELF_AUTH_ENABLED` ha sido eliminado.** Ya
  no hay segundo emisor al que volver: `SecurityConfig` confía incondicionalmente en los decoders
  autoemitidos (`SelfIssuedJwtConfig`), y se han borrado `LazyIssuerJwtDecoder` y
  `JwtAudienceValidator`. `SecurityConfig` ya no tiene ningún camino de Keycloak.
- **Configuración y entorno limpiados.** `.env`, `.env.example`, `application.yml` y
  `docs/docker-compose.yml` no contienen ninguna variable `OIDC_*`/`KEYCLOAK_*`. El backend arranca
  y autentica sin necesidad de ningún flag.
- **Flujo seguro de contraseña inicial / migración de credenciales.** Se añade el endpoint interno
  `POST /internal/auth/users/{userId}/credentials` y su equivalente Portal
  (`InternalCredentialBootstrapController`), protegido por un secreto compartido
  (`INTERNAL_AUTH_BOOTSTRAP_SECRET`, vacío por defecto → rechaza todo; nunca una funcionalidad de
  producto). Con él se fijan las contraseñas Argon2id de los 8 usuarios de desarrollo (4 nuevos +
  4 migrados, ver `GETTING_STARTED.md` §5), todos verificados autenticándose contra el emisor
  propio con Keycloak apagado/eliminado.
- **Recuperación de contraseña con transporte local real.** Se añade `SmtpEmailSender`
  (`brika.notifications.email-transport=smtp`, local-only) apuntando al contenedor **Mailpit** del
  stack; el flujo de recuperación queda verificado de extremo a extremo: solicitud → email en
  Mailpit → enlace con token de un solo uso → confirmación → login con la nueva contraseña. Sigue
  sin existir un proveedor de email de producción (decisión pendiente, ADR-NOTIF-001 D8-2).
- **Verificación final (cierre formal de Sprint 22).** Backend `mvn verify`: **409/409 tests, 0
  fallos** (100 unitarios + 309 integración). Frontend: **397/397 tests en 86 ficheros, 0 fallos**,
  `ng lint` y `ng build` en verde. Prueba real de extremo a extremo contra el stack local sin
  Keycloak: login por rol (SUPERADMIN/MANAGER/BROKER/CLIENT), JWT autoemitido aceptado por
  endpoints protegidos, rechazo de token manipulado/expirado, rotación de refresh token, detección
  de reutilización (revocación de familia), logout con revocación, bloqueo tras 5 intentos
  fallidos (429), aislamiento interno↔Portal (401 cruzado), aislamiento de tenant (404 enmascarado
  entre empresas), RBAC (403 por permiso insuficiente) y recuperación de contraseña completa vía
  Mailpit.

**Consecuencias:** ningún componente activo de Brika depende de Keycloak; el emisor propio es el
único en todos los entornos. `19_IDENTITY_OAUTH_SPECIFICATION.md` queda actualizada con una nota de
estado (su modelo de proveedor OIDC externo describe el diseño original, superado por esta
decisión). `GETTING_STARTED.md` queda reescrito sin referencias a Keycloak. Sigue pendiente (fuera
del alcance de Sprint 22) la selección de un proveedor de email real, la migración de usuarios de
producción y un endpoint de administración de contraseñas con RBAC/auditoría/UX (los puntos
pendientes del cierre se enumeran en el informe de cierre).

## ADR-ENV-001 — Entornos, claves JWT, email y seed reproducible (Sprint 24)

**Contexto:** la base de configuración de BRIKKA trataba todos los arranques por igual
(`application.yml` con defaults local-friendly, email `noop` por defecto en todo, claves JWT
efímeras por proceso si no se fijan, sin seed reproducible de empresa/usuarios/bancos y sin
configuración de entorno para el frontend). Sprint 24 exige separar LOCAL/TEST/PROD, hacer
persistentes las claves JWT, soportar email SMTP real y un seed reproducible e idempotente,
manteniendo intacta la autenticación existente (Argon2id, JWT RS256, separación Internal/Portal,
refresh tokens opacos, rotación, single-use, anti-enumeración, lockout, bootstrap interno). No se
rehace nada del mecanismo de autenticación (regla del sprint §5/§44).

**Decisión:**
1. **Perfiles Spring** `application-{local,test,prod}.yml` sobre un `application.yml` común
   (baseline local-friendly) para no romper los ITs que corren sin perfil. `local`: email `smtp` →
   Mailpit, seed habilitado, CORS del dev server. `test`: email `test` (sender en memoria), seed
   deshabilitado. `prod`: email `smtp` siempre, seed prohibido, CORS estricto desde env.
2. **PROD fail-closed**: `ProdEnvironmentValidator` (un `EnvironmentPostProcessor`) aborta el
   arranque en PROD si falta cualquier secreto (claves JWT, `SMTP_HOST`), si el email no es `smtp`,
   si el seed queda habilitado, o si CORS contiene comodines/localhost.
3. **Claves JWT persistentes**: se reutiliza la lectura existente de
   `brika.security.self-auth.{internal,portal}-signing-key-pem` (base64 PKCS8 DER); se añade
   `scripts/generate-jwt-keys.sh` y el `.gitignore` de `.secrets/`. En local siguen siendo opcionales
   (efímeras si vacías); en PROD obligatorias. Test que verifica que un token sobrevive un "reinicio"
   con la misma clave persistida.
4. **Argon2id elevado**: de `defaultsForSpringSecurity_v5_8()` (16 MiB / 2 iter) a
   `new Argon2PasswordEncoder(16, 32, 1, 32768, 3)` (32 MiB / 3 iter / 1 paralelo), cumpliendo OWASP
   con margen. No invalida hashes existentes (el hash embebe sus propios parámetros). Se mantiene el
   algoritmo Argon2id.
5. **Email**: el transporte en PROD es siempre `smtp` (nunca `noop`, ADR-NOTIF-001 D8-2); se añaden
   las variables `SMTP_HOST/PORT/USERNAME/PASSWORD/FROM/FROM_NAME/TLS/AUTH`. Local mantiene Mailpit.
   Se mantiene la abstracción `EmailSender`/`SmtpEmailSender`/`NoOpEmailSender` y los notifiers de
   reset. Test real de password-reset: el correo se entrega por SMTP a Mailpit y el enlace que viaja
   dentro del mensaje real es el que se consume.
6. **Seed reproducible**: `DevSeedRunner` (`CommandLineRunner`, `@Profile({"local","test"})` +
   `@ConditionalOnProperty(brika.seed.enabled)` + fail-closed en PROD) siembra de forma idempotente
   la empresa demo, `superadmin@brika.local`/`manager@brika.local`/`broker@brika.local` y un catálogo
   de bancos, fijando contraseñas Argon2id solo si el usuario aún no tiene credencial (no pisa
   contraseñas cambiadas). En local está habilitado; en test deshabilitado por defecto; en PROD
   prohibido.
7. **Frontend**: se añade `environment.production.ts` y `fileReplacements` en
   `angular.json` (build `production`), de modo que la URL de la API procede del entorno.

**Consecuencias:** la regla "email `noop` por defecto en todo entorno" de ADR-NOTIF-001 queda
**superada** para PROD (siempre `smtp`) y LOCAL (Mailpit); `noop` solo persiste para entornos sin
perfil y queda prohibido en PROD por validación. El par efímero de JWT queda limitado a local/test.
El seed de demo nunca puede ejecutarse en producción. `GETTING_STARTED.md`, `10_DEVOPS.md` y
`23_CLOUD_DEPLOYMENT_SPECIFICATION.md` quedan actualizados (este último deja de citar OIDC).

**Estado:** APPROVED (implementado en Sprint 24).

## ADR-NOTIF-002 — Eventos de dominio conectados a notificaciones IN_APP (Sprint 25)
**Estado:** DECIDIDO (implementado en Sprint 25)

**Problema:** la infraestructura de notificaciones (NotificationService, NotificationController,
NotificationRepository, modelo, endpoints y UI) existía desde Sprint 8/17, pero NotificationService
no tenía ningún productor: ningún evento real creaba notificaciones ("No producer exists yet").

**Decisión:**
1. **Mecanismo**: se crea el seam `NotificationPublisher` (interfaz) +
   `SynchronousNotificationPublisher` (impl síncrona en la misma transacción que la operación),
   espejo del patrón `ActivityPublisher`/`SynchronousActivityPublisher` (Sprint 3, Decisión A). Si la
   operación principal falla, no queda notificación falsa (misma transacción). Sin Kafka/RabbitMQ/
   WebSockets/SSE en este sprint; el seam permite un swap async posterior (20_RABBITMQ_SPECIFICATION.md).
2. **Destinatarios**: resolución centralizada en `NotificationRecipients` a partir de relaciones
   reales (case_assignments activos, case_clients, conversation_participants activos). Reglas: el
   actor nunca es destinatario; nunca se notifica a otra empresa; SUPERADMIN no recibe todo; una
   acción = exactamente una notificación por destinatario (sin duplicados).
3. **Eventos conectados** (types centralizados en `NotificationType`):
   - Caso: `CASE_STATUS_CHANGED` (changeStatus), `CASE_CANCELLED` (cancel), `CASE_REOPENED`
     (reopen) → usuarios asignados del caso salvo el actor.
   - Documentos: `DOCUMENT_UPLOADED` (subida por usuario → asignados salvo subidor; subida por
     cliente Portal → asignados), `DOCUMENT_REVIEWED` (→ quien subió la versión revisada),
     `DOCUMENT_PUBLISHED` (→ clientes del caso, `recipient_client_id`).
   - Mensajes: `NEW_MESSAGE` — usuario envía → asignados del caso salvo el autor; en conversación
     CLIENT también los participantes cliente (Portal); cliente Portal envía → asignados internos.
     Para garantizar atomicidad, el envío de mensajes se extrae a `ConversationMessageService`
     (`@Transactional`); los controladores ya no insertan mensajes directamente.
4. **Contador de no leídas**: nuevos endpoints `GET /notifications/unread-count` y
   `GET /portal/notifications/unread-count` (scoped al usuario/cliente llamante) para el badge del
   frontend. El badge del sidenav se actualiza al cargar y en cada navegación (sin polling).
5. **Email**: fuera de alcance del sprint; IN_APP obligatorio, el dispatcher existente sigue
   registrando la delivery (Sprint 8 D8-1/D8-2).

**Consecuencias:** la nota "no producer exists" de ADR-NOTIF-001 queda superada para estos eventos;
los comentarios de NotificationService/NotificationRepository y del frontend que la citaban se
actualizan. Portal no se convierte en subproyecto: reutiliza el mismo modelo/API con su propio
endpoint de contador.

**Estado:** APPROVED (implementado en Sprint 25).

## ADR-NOTIF-003 — Transporte asíncrono de notificaciones por RabbitMQ (Sprint 26)
**Estado:** DECIDIDO (implementado en Sprint 26)

**Problema:** en Sprint 25 las notificaciones IN_APP se escriben en la misma transacción que la
operación (SynchronousNotificationPublisher). El roadmap (ADR-NOTIF-001, 20_RABBITMQ_SPECIFICATION.md)
prevé entregarlas de forma asíncrona vía RabbitMQ para desacoplar la escritura de `notifications` de la
latencia de la operación, sin romper los eventos, destinatarios ni aislamiento de Sprint 25.

**Decisión:**
1. **Toggle de transporte** (`brika.notifications.transport`): `sync` (default, `matchIfMissing`) →
   `SynchronousNotificationPublisher`; `rabbitmq` → `RabbitMqNotificationPublisher`. Ambos beans están
   anotados con `@ConditionalOnProperty`, así que en cualquier momento existe exactamente un
   `NotificationPublisher`. Los productores (CaseService/DocumentService/ConversationMessageService) y
   las reglas de destinatarios de Sprint 25 no cambian; el seam `NotificationPublisher` se mantiene.
2. **Evento en el bus**: por destinatario se publica un `NotificationRequestedEvent`
   (`eventType=notification.requested`) con el envelope de la spec (eventId, occurredAt, companyId) más
   el destinatario resuelto (recipientUserId o recipientClientId), el tipo y un payload simple de
   claves/valores. Nunca se envían entidades JPA ni datos sensibles. Los destinatarios se resuelven en
   los productores (una sola fuente de reglas); el consumer es un paso delgado que solo llama a
   NotificationService.create, sin lógica Case/Document/Conversation.
3. **Transaccionalidad**: la publicación se difiere a after-commit mediante
   `TransactionSynchronizationManager.afterCommit()`. Si la transacción de negocio se revierte, el
   mensaje no se publica (no hay notificación falsa). Es la prioridad 2 de la spec (§6: "publicación
   after-commit si resulta suficiente"); NO se implementa Transactional Outbox completo porque no es
   necesario para este caso (mismo trade-off documentado en la spec).
4. **Topología RabbitMQ** (nombres no fijados por la spec; se eligen coherentes con "colas separadas
   por responsabilidad"): exchange `brika.events` (topic), cola durable `brika.notifications.queue`
   enlazada por `notification.requested`, y DLX/DLQ (`brika.events.dlx` / `brika.notifications.dlq`).
   Retry limitado con backoff (spring retry: 3 intentos, backoff 1s→2s→4s… máx 30s) y, al agotar,
   dead-letter a la DLQ (spec §5).
5. **Idempotencia**: el envelope lleva `eventId` para poder deduplicar en el futuro; en operación
   normal cada acción publica exactamente una vez por destinatario (after-commit sobre una transacción
   que commitea una vez), por lo que no hay duplicados accidentales. No se construye tabla de
   deduplicación en este sprint.
6. **Prueba con RabbitMQ real**: IT obligatorio con broker real. En esta máquina (~2 GB de RAM) un
   RabbitMQ de Testcontainers entra en `system_memory_high_watermark` y nunca abre el puerto AMQP; por
   ello el IT `NotificationAsyncIntegrationIT` se conecta al broker local `brika-rabbitmq`
   (docs/docker-compose.yml, localhost:25672) y purga la cola antes de cada test para aislar. En un
   host con más memoria se prefiere Testcontainers RabbitMQ. El consumer tiene su propio test unitario.
7. **Idempotencia/Autorización (spec §8)**: el consumer revalida lo esencial (exactamente un
   destinatario, tipo presente) y reutiliza NotificationService, que impone las mismas reglas; la
   consulta/lectura de notificaciones sigue pasando por los endpoints scoped al llamante (Sprint 25),
   por lo que el aislamiento multi-tenant no se debilita.

**Consecuencias:** la escritura de `notifications` queda desacoplada de la operación cuando el
transporte es `rabbitmq` (asíncrona), mientras el default `sync` preserva el comportamiento de Sprint
25 para el resto de la suite y despliegues que no quieran broker. Los ITs síncronos no tocan RabbitMQ
(no hay beans AMQP en modo sync). El ADR no introduce WebSockets/SSE/polling ni cambio de API/frontend.

## ADR-RBAC-002 — SUPERADMIN como administrador global (Sprint 27)
**Estado:** DECIDIDO (implementado en Sprint 27, Bloque 1)

**Problema:** carencia 1 del sprint: SUPERADMIN no puede acceder a casi ninguna pantalla. El SUPPORT_SESSION
(ADR-RBAC-001) no está implementado, y `AuthorizationService.requireTenant` rechaza a SUPERADMIN (no tiene
empresa), con lo que todo endpoint scoped por tenant devolvía 403 y el rol quedaba inútil en la práctica.

**Decisión:**
1. **SUPERADMIN = administrador global (GLOBAL), nunca un bypass.** Los permisos se siguen comprobando
   (`requirePermission`); lo único que cambia es la resolución del tenant: en vez de `requireTenant`
   (que devuelve empty para SUPERADMIN), el tenant se resuelve desde el **recurso accedido**. Es el mismo
   patrón que ya usaba `CompanyController` para la rama global.
2. **Lecturas globales**: las pantallas tenant (Casos, Clientes, Tareas, Usuarios, Actividad) y los
   recursos scoped por caso (documentos, conversaciones, simulaciones, financiación, ofertas) pasan por
   `isSuperadmin()` o por `CaseAccessService` (que resuelve el tenant del case), devolviendo los datos de
   todas las empresas. `NOTIFICATION_READ` queda fuera (notificaciones son personales; SUPERADMIN sigue
   sin ese permiso).
3. **Escritura administrativa global**: creación de usuario por SUPERADMIN requiere `companyId` explícito
   en `CreateUserApiRequest` (el rol global no tiene empresa propia); update/disable resuelven el tenant
   del usuario objetivo. Para el resto (MANAGER/BROKER) `companyId` se ignora: sigue la regla "el tenant
   nunca viene del cliente".
4. **Escrituras operativas de tenant (crear/editar caso, cliente, tarea)**: siguen atadas al tenant del
   llamante. Hasta que exista SUPPORT_SESSION no tiene sentido que SUPERADMIN las haga sin elegir empresa;
   el frontend **oculta** esos botones de creación para SUPERADMIN (directiva `appHideForRole`) para no
   producir el 403 sin justificación que el sprint prohíbe (§8), en vez de exponerlos y que el backend los
   rechace. Es la alternativa aceptada en lugar de un selector de empresa en cada pantalla (Sprint 28+).
5. **No se debilita el aislamiento** entre usuarios de tenant (MANAGER/BROKER/CLIENT): para ellos
   `requireTenant` y las comprobaciones de pertenencia/sin asignación se mantienen intactas.

**Consecuencias:** SUPERADMIN puede navegar y leer todas las pantallas y administrar usuarios/empresas a
nivel global; la creación de registros operativos de tenant desde la UI de SUPERADMIN queda aplazada a
SUPPORT_SESSION (Sprint 28+). ADR-RBAC-001 (SUPPORT_SESSION pendiente) sigue vigente para ese alcance.

## Adenda Sprint 34 — Hardening, auditoría UI/UX y bugs encontrados

**Contexto:** Sprint 34 pidió una auditoría integral (no una lista de funcionalidades nuevas) antes de
tocar código, con foco explícito en un bug conocido de scroll horizontal en el sidebar y en una revisión
completa de scrolls/cards/tablas/dialogs. Se documentan aquí los hallazgos reales (no supuestos) y sus
correcciones, todas con causa raíz identificada y verificada en vivo con navegador real.

**D34-1 — Sidebar: scroll horizontal (causa raíz: empate de especificidad CSS).**
`sidenav.component.scss` fijaba `::ng-deep .mdc-list-item { width: calc(100% - var(--space-4)); margin:
2px var(--space-2); }` — margen y ancho pensados para sumar exactamente el 100% del contenedor (8px + 224px
+ 8px = 240px). Pero Angular Material define su propia regla global `.mat-mdc-list-item, .mat-mdc-list-option
{ width: 100% }`, con la MISMA especificidad (una clase) que la regla del proyecto — el empate se resuelve
por orden de aparición en la hoja de estilos compilada, y la de Material aparecía después, ganando siempre.
Resultado: cada elemento del menú medía 240px de ancho MÁS 16px de margen (256px) dentro de un contenedor de
240px, desbordando 8px — de ahí el scroll horizontal. Verificado con `document.styleSheets` en el navegador
real (las dos reglas, su especificidad y cuál ganaba) antes de tocar nada. **Corrección:** combinar ambas
clases en un único selector (`.mdc-list-item.mat-mdc-list-item`), subiendo la especificidad del proyecto sin
depender del orden de carga. Verificado en vivo: `scrollWidth === clientWidth` en escritorio, tablet y móvil
(modo overlay incluido).

**D34-2 — Spinners de carga que nunca desaparecen tras un error (patrón sistémico, no un caso aislado).**
Encontrado al probar un caso inexistente: `case-detail.component.html` mostraba el mensaje de error
correctamente pero también un `<mat-spinner>` que giraba para siempre, porque su condición
(`@if (theCase() === null)`) era un bloque `@if` independiente del banner de error, sin ninguna relación
entre ambos — si `loadCase()` fallaba, `theCase()` nunca dejaba de ser `null`, así que el spinner de esa
condición no tenía forma de desaparecer. Se encontró el mismo patrón, ya corregido dentro de la propia base
de código como referencia correcta, en `client-detail.component.html` (usa `@else if`, no dos `@if`
separados) — lo que confirma que el patrón correcto ya existía en el proyecto y este era un caso de
inconsistencia, no una decisión de diseño. Al revisar el resto de la aplicación, el mismo antipatrón (dos
`@if` independientes en vez de `@if/@else if`) apareció también en `bank-detail.component.html`,
`company-detail.component.html` y `portal-case-detail.component.html` — los 4 corregidos con la misma
solución mínima (`@if (!error()) { spinner }` anidado). Además, dentro de `case-detail.component.ts` se
encontró que **14 sub-paneles** (asignaciones, clientes, documentos, solicitudes de documentos,
simulaciones, financiación, matching, solicitudes a banco, ofertas, tareas, conversaciones, análisis
financiero, contrato, dossier) compartían la causa raíz más profunda: su `error` callback solo escribía en
la señal de error global/local, sin resolver nunca la señal de datos (`assignments()`, `clients()`,
`documents()`, etc.) a un valor no-nulo — dejando el spinner de esa sección concreta girando para siempre
en cualquier fallo de red, no solo en un 404 del caso. **Corrección de raíz:** cada `error` callback ahora
también resuelve su propia señal a un valor vacío seguro (`[]`, o `{ documentId: null, versions: [] }` para
contrato/dossier) — el mismo patrón ya usado correctamente por `loadProperty`/`loadCaseFee` en el mismo
archivo. Verificado en vivo (navegación a un caso inexistente: el spinner superior desaparece) y con test
de regresión en `case-detail.component.spec.ts` y `portal-case-detail.component.spec.ts` (donde ya existía
un test de "carga falla" que extender). No se ha añadido un test nuevo desde cero para `bank-detail`/
`company-detail` por no tener un test de este escenario previo — queda documentado como limitación honesta,
no como cobertura inventada.

**D34-3 — Test de CI intermitente, no relacionado con los cambios de este sprint.**
`conversation-detail-dialog.component.spec.ts` (sin tocar desde Sprint 17) fallaba por timeout de 5000ms
al ejecutarse dentro de la suite completa de 449 tests, pero pasaba en ~10ms al ejecutarse en solitario —
confirma contención del pool de workers de Vitest bajo carga, no un test colgado de verdad (es un test
100% síncrono, sin temporizadores). Corrección mínima y de bajo riesgo: timeout explícito de 15000ms en
ese único test.

**D34-4 — IA local con Ollama: código completo y probado, instalación real no completada.**
Ver `docs/09_AI.md` §"IA local con Ollama" para la arquitectura (generalización de `_resolve_provider` en
`ai-worker/main.py`, `AI_PROVIDER=ollama`, 11 tests contra un servidor Ollama falso). El único intento de
instalación real (`brew install ollama`) derivó en compilar LLVM desde código fuente como dependencia
transitiva — un proceso de horas que llevó la carga de la máquina a más de 240 y causó fallos reales en
`mvn verify` (Testcontainers sin recursos para arrancar Postgres) antes de que la máquina se reiniciara por
sí sola. No se ha reintentado la instalación real sin que el usuario lo autorice explícitamente de nuevo,
dado el impacto ya observado. El proveedor Ollama está completo, probado y documentado, pero **no
validado contra una instalación real de Ollama en este sprint** — se documenta como limitación honesta, no
como funcionalidad fingida (mismo criterio que Sprint 33 aplicó a `ANTHROPIC_API_KEY`).

**Consecuencias:** `sidenav.component.scss`, `case-detail.component.{html,ts,spec.ts}`,
`bank-detail.component.html`, `company-detail.component.html`, `portal-case-detail.component.{html,spec.ts}`,
`conversation-detail-dialog.component.spec.ts` modificados. Sin migraciones, sin permisos nuevos, sin
endpoints nuevos, sin cambios de arquitectura backend.

**Documentos afectados:** este documento, `docs/09_AI.md`.

**Estado:** APPROVED. Implementado y validado en Sprint 34.

## Adenda Sprint 36 — Validación funcional integral, UX final y consolidación

**Contexto:** Sprint 36 pidió una validación integral de Brika como si fuera un producto real —
auditoría funcional completa de todos los módulos, matriz RBAC y multi-tenant con evidencia HTTP real,
auditoría UX/UI final, responsive en 4 breakpoints, flujo end-to-end completo, seguridad y regresión —
sin asumir que nada funciona por haber funcionado en un sprint anterior. Se documentan aquí los hallazgos
reales con causa raíz, no una lista de comprobaciones superficiales.

**D36-1 — Tabla de documentos del caso: botones de acción apilados en 6 líneas en móvil (causa raíz:
celda sin el wrapper `.table-actions` ya existente en el proyecto).**
Encontrado al auditar `case-detail` a 375px: la celda de acciones de la tabla de documentos contiene 6
botones (Subir versión, Versiones, Descargar, Revisar, Publicar, Despublicar) declarados como `inline-flex`
sueltos dentro de un `<td>` de 137px de ancho, sin ningún contenedor que fuerce `white-space: nowrap` — cada
botón se apilaba en su propia línea, convirtiendo cada fila de documento en un bloque de 6 líneas de alto,
difícil de escanear en pantallas estrechas. `styles.scss` ya define `.table-actions` (creado en Sprint 14,
comentario explícito: "evita que se apilen en varias líneas cuando la celda se estrecha... la tabla ya se
desplaza horizontalmente vía `.table-scroll`") pero esa clase no se usaba en ningún punto de
`case-detail.component.html` — el wrapper existe en el codebase pero nunca se aplicó en este componente.
**Corrección:** se envuelve el contenido de las 4 celdas de acciones multi-botón de `case-detail.component.html`
(documentos [6 botones], solicitudes de documentos [2], solicitudes a bancos [2], tareas [hasta 3]) en
`<div class="table-actions">`, reutilizando el patrón ya establecido — ninguna clase ni comportamiento
nuevo. Verificado en vivo a 375px: cada fila pasa a ocupar una sola línea, con las acciones alcanzables
mediante el scroll horizontal contenido que la tabla ya tenía (`.table-scroll`, `overflow-x: auto`); no se
oculta ni se recorta contenido, tal y como exige el sprint. Las celdas de acción de un solo botón (clientes,
matching bancario, ofertas, conversaciones) no se tocan, por no presentar el problema.

**D36-1b — Formularios sin mensaje de validación visible (causa raíz sistémica: `mat-error` casi
nunca se usa en el proyecto; clasificado P1, corrección demostrada y aplicada al formulario probado,
el resto documentado para un sprint dedicado).**
Al auditar el formulario "Nuevo cliente" con un email inválido (`not-an-email`), Angular marca el
campo correctamente como inválido (`ng-invalid mat-form-field-invalid ng-touched` presentes en el
DOM — la validación **sí** se ejecuta: `Validators.required`/`Validators.email` en
`client-form.component.ts`), pero no aparece ningún mensaje explicando el motivo: el formulario no
contiene ni un solo `<mat-error>`. El usuario solo ve el campo en rojo y el botón "Guardar" sin
efecto — el mismo síntoma de "envío silenciosamente bloqueado" que obligó a depurar por consola en
repetidas ocasiones a lo largo de este mismo sprint (combobox de tipo de operación en creación de
caso, 4º campo de simulación, campo de identificador de usuario). Comprobado el alcance real:
36 ficheros del proyecto usan `mat-form-field`, y solo 2
(`edit-financial-profile-dialog.component.html`, `case-dialogs/cancel-dialog.component.html`)
contienen algún `<mat-error>` — 34 formularios, incluidos los de operaciones principales (creación/
edición de caso, usuario, empresa, documento, tarea, inmueble, honorarios, conversaciones, bancos),
no muestran ningún motivo de error de validación. **Corrección aplicada y verificada en vivo:**
`client-form.component.html` (email, nombre, apellidos, teléfono) usando el patrón ya existente en
el proyecto (`@if (form.controls.X.hasError('tipo')) { <mat-error>...</mat-error> }`, copiado de
`edit-financial-profile-dialog.component.html`) — verificado con el navegador real: "Introduce un
email válido." y "El nombre es obligatorio." aparecen correctamente al invalidar los campos; test de
regresión añadido en `client-form.component.spec.ts`. **No se corrigen los 33 ficheros restantes en
este sprint**: aunque el patrón es mecánico y de bajo riesgo, aplicarlo de forma consistente a 33
formularios (con sus mensajes de error específicos por campo) es un cambio de alcance comparable a
un rediseño transversal, expresamente restringido por este sprint salvo necesidad demostrada — la
necesidad está demostrada (es P1: UX crítica que afecta a las operaciones principales de la
aplicación), pero la corrección completa se recomienda como alcance íntegro de un sprint dedicado,
no como añadido a este. Lista completa de ficheros pendientes disponible en el informe de cierre de
Sprint 36.

**D36-2 — Diálogos apilables en `case-detail` sin guarda anti-duplicado (hallazgo documentado, sin
corregir — fuera del alcance proporcionado de este sprint).**
Reproducido de forma concreta: al abrir "Nueva simulación" con el formulario inválido (falta un campo) y
volver a invocar la apertura de un diálogo desde la misma pantalla, Angular Material apila una segunda
instancia de `MatDialog` sobre la primera sin avisar. Causa raíz: ninguno de los ~15 métodos
`open*()` de `case-detail.component.ts` (p. ej. `openCreateSimulation`, líneas 511-520) comprueba si ya
hay un diálogo abierto antes de llamar a `this.dialog.open(...)` — es un patrón repetido de forma
idéntica en todo el fichero, no un fallo aislado. Verificado con
`document.querySelectorAll('mat-dialog-container')`: ambas instancias coexisten con `offsetParent` no nulo.
Sin pérdida de datos (confirmado por `curl` directo: solo se persiste una fila de simulación correcta).
Clasificado **P2**: visible pero de disparo poco frecuente (requiere reabrir un diálogo con uno ya
abierto), recuperable cancelando el diálogo obsoleto, sin riesgo de integridad. No se corrige en este
sprint por ser un cambio transversal a ~15 métodos en un único fichero — excede el alcance de "corrección
puntual de un hallazgo real" y entra en terreno de refactor, expresamente prohibido salvo necesidad
demostrada. **Recomendación para un sprint futuro:** guarda genérica basada en `MatDialog.openDialogs.length`
o un flag de "diálogo en curso" compartido entre los métodos `open*()`.

**D36-3 — Discrepancia entre capturas de pantalla del panel de vista previa y el DOM real a anchos
emulados (limitación de herramienta, no un bug de Brika).**
Durante la auditoría responsive a 768px, las capturas de pantalla mostraban el contenido de página
completa (dashboard, clientes, detalle de caso) confinado a una columna de ~230px con una franja vacía a
la derecha, en varias pantallas no relacionadas entre sí. Antes de documentar esto como un hallazgo de
Brika, se contrastó con el DOM real: `window.innerWidth`/`visualViewport.width` reportaban 768 de forma
consistente, `getBoundingClientRect()` de cada contenedor en la cadena de ancestros
(`app-case-detail` → `mat-sidenav-content` → `mat-sidenav-container` → `body` → `html`) medía el ancho
completo del viewport, y `overflow`/`overflow-x` era `visible` en todos los niveles salvo el contenedor de
Material (`hidden`, esperado). El mismo patrón de columna estrecha se reprodujo de forma idéntica en
páginas sin relación funcional entre sí (dashboard, clientes, detalle de caso), lo que descarta una causa
específica de un componente. Con el preset `desktop` (tamaño nativo) la herramienta capturó una imagen
mucho más pequeña (395×312) pero proporcionalmente correcta, sin el patrón de columna estrecha. Conclusión,
con código y ejecución real como fuente de verdad (tal y como exige este sprint): es un artefacto de
renderizado del panel de vista previa al emular anchos móviles/tablet, no un defecto de la aplicación. La
validación responsive de este sprint se apoyó por tanto en aserciones JS
(`document.body.scrollWidth > window.innerWidth`) como señal autoritativa en vez de en la inspección visual
de las capturas a esos anchos — sin overflow horizontal detectado en dashboard, clientes ni detalle de caso
a 375/768/1024/1440px.

**D36-4 — Sin forma de reactivar un usuario deshabilitado, en ningún rol (funcionalidad pendiente,
fuera de alcance de este sprint).**
Al deshabilitar un usuario desde `/app/users` como MANAGER, la fila deja de mostrar cualquier acción de
reactivación (el botón "Deshabilitar" desaparece y no aparece uno de "Habilitar"). Verificado a nivel de
código, no solo de UI: `UserController.java` solo expone `GET` (lista/detalle), `POST` (crear), `PATCH`
(editar) y `POST /{id}/disable` — **no existe ningún endpoint `enable`/`reactivate`** en todo el backend,
para ningún rol, incluido SUPERADMIN. No es una omisión de la UI que oculte una capacidad real del
backend: la capacidad de reactivar un usuario deshabilitado, una vez deshabilitado, **no existe en el
producto**, solo sería posible con un `UPDATE` directo en base de datos. **Clasificación:** no es un bug
(nada se comporta de forma incorrecta ni inconsistente) ni claramente "intencional" (no hay ADR ni
comentario que lo declare como decisión de producto) — es funcionalidad pendiente. **Fuera de alcance de
Sprint 36**: corregirlo requeriría añadir un endpoint nuevo (`POST /api/v1/users/{id}/enable` o
equivalente) y su acción en la UI — funcionalidad nueva, expresamente prohibida en un sprint de
validación. Documentado como recomendación para un sprint de producto futuro, no como hallazgo a
corregir aquí.

**Auditoría RBAC y multi-tenant — resultado real (Gates 5-7):** validado con los 4 roles reales
(`superadmin@brika.local`, `manager@brika.local`, `broker@brika.local`, `client@brika.local`) autenticados
por HTTP real contra `/api/v1/auth/login` y `/api/v1/portal/auth/login`. Confirmado: BROKER recibe 403 en
`GET /api/v1/companies` (coherente con `V9__seed_role_permissions.sql`, que no concede `COMPANY_READ` a
BROKER, y con el guard de navegación `nav-items.ts` que oculta el enlace "Empresas" bajo ese mismo
permiso — backend y frontend alineados). CLIENT recibe 401 en todos los endpoints internos y 200 en
`/api/v1/portal/cases` (separación física de cadenas de filtros por emisor de JWT, confirmada). SUPERADMIN
ve las 3 empresas de ambos tenants (`GET /api/v1/companies`), consistente con ADR-RBAC-002 (administrador
global, no un bypass accidental). Aislamiento multi-tenant confirmado con un segundo tenant real
(`demo.manager@brika.test`, tenant "Demo Broker") contra clientes (lectura y escritura), casos, usuarios y
documentos del tenant "Brikka Dev": en todos los casos, acceso cruzado devuelve `404` enmascarado (nunca
`403`, que revelaría la existencia del recurso) — `CLIENT_NOT_FOUND`, `CASE_NOT_FOUND`, `USER_NOT_FOUND`.
Sin token, `401` en endpoints protegidos.

**Seguridad (Gate 17) — resultado real:** `.env` y `.secrets/` fuera de git (`git ls-files` verificado);
CORS restringido a orígenes configurables, sin wildcard, `allowCredentials: false`, cabeceras limitadas a
`Authorization`/`Content-Type` (`SecurityConfig.java`), y en `prod` el valor por defecto es vacío (falla
cerrado si no se configura explícitamente) frente a `local`/`test` que sí traen `http://localhost:4200` por
defecto. Callback de IA (`/internal/ai/document-extractions/{id}/callback`) protegido por secreto
compartido fuera de `/api/v1`, rechaza sin él. URL de descarga presignada verificada con una llamada real:
URL de MinIO con firma `AWS4-HMAC-SHA256` válida y `expiresInSeconds: 300`. Rotación/invalidación de
refresh token verificada por HTTP real: tras `logout`, reutilizar el mismo `refreshToken` en `/auth/refresh`
devuelve `401 UNAUTHENTICATED`.

**Consecuencias:** `case-detail.component.html` modificado (D36-1); `client-form.component.html` y
`client-form.component.spec.ts` modificados (D36-1b). Sin migraciones, sin permisos nuevos, sin
endpoints nuevos, sin cambios de arquitectura backend ni frontend.

**Documentos afectados:** este documento, `docs/09_AI.md`.

**Estado:** APPROVED. Implementado y validado en Sprint 36.

## Adenda Sprint 37 — Consolidación de validaciones UX, diálogos y ciclo de vida de usuarios

**Contexto:** Sprint 37 resuelve los tres pendientes reales identificados y documentados en Sprint 36
(D36-1b, D36-2, D36-4), sin ampliar alcance a nada más.

**D36-1b — resuelto en su totalidad.** El inventario real (no asumido) confirmó exactamente 33
ficheros con `mat-form-field` sin ningún `<mat-error>`, los mismos 33 identificados en Sprint 36 —
32 usan únicamente `Validators.required`, 2 combinan `required` + `email`
(`portal-profile.component.ts`, `user-form.component.ts`); ningún fichero pendiente usa
`min`/`minLength`/`maxLength`/`pattern` (esos validators solo aparecían ya en los 3 ficheros que
Sprint 36 dejó correctamente cubiertos). Se aplicó el patrón ya validado
(`@if (form.controls.X.hasError('tipo')) { <mat-error>...</mat-error> }`) a los 33 ficheros,
reutilizando los `Validators` existentes sin modificarlos. Casos especiales resueltos sin aplicar el
patrón mecánicamente: `mat-checkbox` (`isPrimary` en `add-client-dialog`) excluido por no soportar
`mat-error` igual que un `mat-form-field`; el campo `clientIds` de `create-conversation-dialog`
(selección múltiple sin `Validators` de Angular, validado manualmente en `submit()`) se dejó tal
cual — ya tenía feedback visible vía el banner de error existente, añadir `mat-error` ahí habría sido
mecánico sin sentido. 5 ficheros de test ampliados con un test de regresión cada uno, cubriendo los
patrones distintos presentes en el proyecto (required simple, required+email, mat-select required).
Verificado en navegador real tras el cambio: el combobox "Tipo de operación" de creación de caso —
el mismo campo que bloqueaba silenciosamente el envío en sesiones anteriores — ahora muestra
"Selecciona un tipo de operación." en rojo.

**D36-2 — resuelto con una guarda mínima, sin refactor.** Se confirmó que `MatDialog` (Angular
Material 22) expone `openDialogs: MatDialogRef<any>[]` como getter público — la API estándar del
propio framework para esto, no una construcción propia. No existía ningún wrapper/guard reutilizable
en el proyecto (`grep` verificado). Solución aplicada: un único método privado
`hasOpenDialog(): boolean { return this.dialog.openDialogs.length > 0; }` en
`case-detail.component.ts`, con `if (this.hasOpenDialog()) return;` como primera línea de los 24
métodos `open*()` — ninguna llamada a `.dialog.open(...)` ni su `.afterClosed().subscribe(...)` se
modificó, cero riesgo de alterar el resultado de cada diálogo. Verificado en vivo: abrir "Asignar" y
disparar `openAddClient()` mientras estaba abierto deja exactamente 1 `mat-dialog-container` en el
DOM (antes de la corrección habría dos); tras cerrar el primero, abrir "Añadir cliente" funciona con
normalidad. 2 tests de regresión añadidos (`does not open a second dialog...`,
`opens a new dialog normally once the previous one has closed...`), simulando `openDialogs` con
`vi.spyOn(dialog, 'openDialogs', 'get')`.

**D36-4 — resuelto: ciclo completo ACTIVO ⇄ DESHABILITADO.** Auditado el modelo real antes de tocar
nada: `users.status` es un `varchar(30)` libre, sin `CHECK` constraint (`V1__initial_schema.sql`);
`disable()` ya era idempotente sin validación de estado previo (mismo patrón que `enable()` sigue
ahora); `UserAuthenticationService` ya exigía `status = 'ACTIVE'` en login/refresh (protección
preexistente, no añadida ahora — verificada con un test nuevo,
`loginForAReenabledUserSucceedsAgain`, simétrico al ya existente
`loginForADisabledUserIsRejected`). **Decisión de permisos, documentada explícitamente en vez de
tomada en silencio:** `14_DEFINITIVE_PERMISSION_CATALOG.md` solo define `USER_DISABLE` para este
campo de estado — no existe `USER_ENABLE` en el catálogo aprobado. Crear un permiso nuevo no
presente en el catálogo habría sido inventar superficie de RBAC para un campo que el propio catálogo
ya trata como una única capacidad conmutable. Se reutiliza `USER_DISABLE` (ya concedido exactamente
a SUPERADMIN y MANAGER, `V9__seed_role_permissions.sql`) para proteger también `POST
/api/v1/users/{id}/enable` — mismos dos roles exigidos por el propio sprint, sin tocar
`role_permissions`, sin migración. Endpoint simétrico a `disable()` en todo: mismo
`resolveTenantForTargetUser` (SUPERADMIN global vía `requireUser`, tenant vía
`requireUserInTenant`/`requireTenant`), mismo patrón de auditoría (`USER_ENABLED`), mismo 404
enmascarado cross-tenant (nunca 403), mismo idempotente-sin-excepción sobre un usuario ya activo.
Frontend: botón "Habilitar" (icono `check_circle`, color primario) simétrico al "Deshabilitar"
existente (icono `block`, color warn) en `user-list.component.html`, mismo diálogo de confirmación
reutilizado (`ConfirmDialogComponent`). Validado end-to-end con evidencia real: usuario de prueba
creado → login 200 → deshabilitado → login 401 → reactivado → login 200 de nuevo, además del mismo
ciclo repetido en la UI real del navegador sobre un usuario que había quedado deshabilitado
literalmente en Sprint 36 (confirma que el fix cubre también el estado heredado, no solo usuarios
nuevos). RBAC del nuevo endpoint verificado por HTTP real: BROKER 403, sin token 401, otro tenant
404 enmascarado, usuario inexistente 404, SUPERADMIN 200 global, Portal CLIENT 401.

**Consecuencias:** `case-detail.component.{ts,spec.ts}` (D36-2); 33 ficheros `*.component.html` +
5 `*.component.spec.ts` (D36-1b, lista completa en el informe de cierre); `UserRepository.java`,
`UserController.java`, `IdentityEndpointsIT.java`, `UserAuthEndpointsIT.java`, `user.service.ts`,
`user-list.component.{html,ts,spec.ts}` (D36-4). Sin migraciones, sin permisos nuevos (reutiliza
`USER_DISABLE`), sin cambios de arquitectura.

**Documentos afectados:** este documento.

**Estado:** APPROVED. Implementado y validado en Sprint 37.

## Adenda Sprint 38 — Auditoría técnica, seguridad y preparación de producción

**Contexto:** Sprint 38 es una auditoría técnica (no funcional) del estado real de Brikka V1:
dependencias, seguridad, tests, Docker, configuración de producción, código muerto y documentación.

**D38-1 — CI llevaba roto desde al menos el cierre de Sprint 35, dando una señal verde falsa
(P1, encontrado y corregido con causa raíz).**
`gh run list` mostró que los últimos 5 workflows de CI en GitHub Actions habían fallado, incluido
el del propio commit de cierre de Sprint 37 (`d98764d`) — mientras que `mvn verify` en local
reportaba 514/514 limpio. Investigado en vez de asumido: el job de CI fallaba con
`Tests run: 408, Failures: 6` — siempre las mismas 6 pruebas, en
`EngagementContractEndpointsIT` y `ViabilityDossierEndpointsIT`, con
`SdkClientException: ... Connect to localhost:19000 ... Connection refused`. Confirmado que el
mismo fallo, con idéntica firma, ya existía en el run de CI del commit de cierre de Sprint 35
(`e9d444d`) — no es una regresión de Sprint 36 ni 37, es un defecto preexistente de aislamiento de
tests. **Causa raíz:** generar un contrato de encargo o un dossier de viabilidad sube el HTML
generado a objectstorage (`DocumentService`/`ViabilityDossierService` → `StorageClient.upload()`,
una llamada S3 real, no simulada) — pero, a diferencia de sus hermanos `DocumentServiceIT` y
`DocumentEndpointsIT` (que sí declaran su propio `@Container MinIOContainer` con
`@DynamicPropertySource` apuntando `brika.storage.*` a él, exactamente el mismo patrón que ya usan
para Postgres), `EngagementContractEndpointsIT` y `ViabilityDossierEndpointsIT` solo declaraban un
`PostgreSQLContainer` — sin ningún MinIO propio, caían al valor por defecto
`brika.storage.endpoint=http://localhost:19000`. En este equipo de desarrollo esas pruebas pasaban
por pura coincidencia (el MinIO de `docker-compose.yml` suele estar levantado durante el desarrollo
local), pero fallan en cualquier máquina o entorno de CI donde no lo esté — exactamente lo que
ocurre en GitHub Actions, que nunca ha tenido un servicio MinIO configurado. **Corrección:**
replicado el patrón exacto ya establecido en `DocumentServiceIT`/`DocumentEndpointsIT` — un
`MinIOContainer` propio por clase, `@DynamicPropertySource` con `brika.storage.endpoint/access-key/
secret-key/bucket` apuntando a él, y creación del bucket de test en un bloque estático. Verificado
en local con el entorno completamente detenido (sin `docker-compose` levantado, comprobado con
`docker ps`) para forzar una prueba real de que ya no dependen de nada externo: ambas clases pasan
limpias de forma aislada (`EngagementContractEndpointsIT` 7/7, `ViabilityDossierEndpointsIT` 8/8).
No ha hecho falta ningún cambio en `.github/workflows/ci.yml` — Testcontainers provisiona su propio
MinIO dentro del propio runner de GitHub Actions, exactamente igual que ya hace con Postgres para
las demás 60+ clases de integración de la suite.

**Impacto real:** durante como mínimo los cierres de Sprint 35, 36 y 37, el pipeline de CI de
GitHub Actions ha estado marcando el job de backend como fallido en cada push a `main` — una señal
que, de haberse revisado, habría exigido investigar en cada uno de esos cierres. El hecho de que
`mvn verify` en local siempre reportara verde ocultaba el problema: los informes de cierre de esos
sprints citaron el resultado de la ejecución local (correcto en sí mismo) sin cruzarlo con el
estado real de CI. Consecuencia adicional: el job `backend-docker` (que incluye el escaneo de
seguridad Trivy en la imagen) tiene `needs: backend` — con el job `backend` en rojo, `backend-docker`
se ha estado saltando (`skipped`) en cada uno de esos runs, así que el escaneo Trivy de la imagen
tampoco se ha ejecutado realmente en ninguno de ellos.

**D38-2 — Dependencias con CVE MEDIUM, verificadas una por una, ninguna explotable en el uso real
de la aplicación (P3, documentado, sin actualización forzada).**
Escaneo real con Trivy v0.74.0 (binario oficial descargado de GitHub releases, no compilado —
evitando la contención de recursos que la compilación de Go desde código fuente causó al intentar
`brew install trivy`) contra `backend/pom.xml` y `frontend/package-lock.json`. **Frontend: 0
vulnerabilidades** (confirmado también por `npm audit`, coincide). **Backend: 0 CRITICAL/HIGH, 6
MEDIUM**, las 4 en dependencias transitivas del BOM de Spring Boot, no declaradas directamente:

| Librería | Versión | CVE | Vía | ¿Explotable aquí? |
|---|---|---|---|---|
| `jackson-databind` | 2.21.4 | CVE-2026-54515, CVE-2026-59889, GHSA-mhm7-754m-9p8w | `compile`, directa (Spring Web) | No — los 3 CVE son sobre bypass de `@JsonView`/`@JsonUnwrapped`; `grep` confirma que ninguna de las dos anotaciones se usa en `src/main/java/` |
| `io.netty:netty-codec-http` | 4.1.136.Final | CVE-2026-59903 | `runtime`, transitiva de `software.amazon.awssdk:netty-nio-client` (AWS SDK) | No — `StorageConfig.java` construye `S3Client`/`S3Presigner` sin `httpClient(NettyNioAsyncHttpClient...)`; el SDK v2 usa su cliente síncrono por defecto, Netty queda en el classpath sin instanciarse nunca |
| `org.apache.logging.log4j:log4j-api` | 2.24.3 | CVE-2026-49844 | `compile`, transitiva de `log4j-to-slf4j` (puente estándar de Spring Boot) | No — solo la API/puente está presente; `log4j-core` no está en el árbol de dependencias, el backend real de logging es Logback (SLF4J), el CVE es sobre codificación JSON de `log4j-core` |
| `org.bouncycastle:bcprov-jdk18on` | 1.80.2 | CVE-2026-0636 | `compile`, directa (claves RSA JWT) | No — el CVE es una inyección LDAP en `LDAPStoreHelper` (validación de certificados vía LDAP); `grep` confirma que el proyecto no tiene código LDAP en ningún punto, BouncyCastle se usa aquí solo para parsear claves PKCS8 |

**Spring Boot 3.5.16 ya es la última versión estable de la serie 3.5.x** (`versions:display-parent-
updates` solo ofrece `4.2.0-M1`, un milestone de una major nueva — no una opción razonable para
este sprint). No existe todavía un parche de Spring Boot que actualice estas 4 dependencias
transitivas. **Decisión:** no forzar overrides manuales de versión en `pom.xml` para estas 4
librerías — cada CVE, verificado uno por uno contra el uso real del código, no tiene ruta de
explotación en esta aplicación; forzar versiones fuera de la matriz de compatibilidad probada por
el propio BOM de Spring Boot sería un riesgo de regresión real a cambio de ningún beneficio de
seguridad real, exactamente lo que este sprint prohíbe ("no actualizar dependencias a ciegas").
Documentado como deuda técnica a revisar cuando Spring Boot publique un parche 3.5.x que las
incluya.

**D38-3 — Las claves JWT persistentes documentadas (`SELF_AUTH_INTERNAL_SIGNING_KEY_PEM`/
`SELF_AUTH_PORTAL_SIGNING_KEY_PEM`) nunca llegaban a la propiedad Spring real que el código
consume (P1, encontrado, corregido y verificado empíricamente de extremo a extremo).**
`SelfIssuedTokenKeys` lee `brika.security.self-auth.{internal,portal}-signing-key-pem` vía
`@Value`, y `ProdEnvironmentValidator` exige esas mismas dos propiedades como obligatorias en
PROD — pero, a diferencia de cada una de las demás propiedades del mismo bloque `self-auth:` en
`application.yml` (todas con su `${VARIABLE_ENTORNO:default}`), estas dos no tenían ningún
placeholder que las conectara con la variable de entorno documentada en `GETTING_STARTED.md`,
`10_DEVOPS.md` y `23_CLOUD_DEPLOYMENT_SPECIFICATION.md`. El binding relajado de Spring Boot solo
mapea automáticamente el *literal* de la ruta de la propiedad (`BRIKA_SECURITY_SELF_AUTH_INTERNAL_
SIGNING_KEY_PEM`), nunca un alias más corto como `SELF_AUTH_INTERNAL_SIGNING_KEY_PEM` — sin el
placeholder explícito, esa variable, por bien fijada que estuviera, era invisible para el código.
**Verificado empíricamente, no solo razonado:** con claves reales generadas y la variable
documentada exportada, el backend seguía registrando "generating an ephemeral RSA key" en cada
arranque, antes de tocar nada. **Corrección:** añadidas las dos líneas que faltaban al bloque
`self-auth:` de `application.yml`, con el mismo patrón `${SELF_AUTH_INTERNAL_SIGNING_KEY_PEM:}` /
`${SELF_AUTH_PORTAL_SIGNING_KEY_PEM:}` que ya usa cada propiedad vecina.

Validar el fix reveló un segundo problema, independiente, en las propias claves de prueba
generadas para probarlo — ver D38-4. Con ambos corregidos, la validación de extremo a extremo
(arranque en limpio, sin backgrounding ni Maven de por medio — `java -jar` directo con timeout
acotado) confirma: (1) el backend arranca sin ningún warning de clave efímera; (2) login real
(`POST /api/v1/auth/login`) emite un token válido; (3) tras **reiniciar** el backend con la misma
configuración, un JWT **emitido antes del reinicio** sigue siendo válido después
(`GET /api/v1/me` → 200) — la prueba real de que la clave es persistente entre reinicios, no solo
de que el arranque no muestra un warning.

**D38-4 — `scripts/generate-jwt-keys.sh` producía claves PKCS1, no PKCS8, en macOS (P2,
encontrado y corregido durante la verificación de D38-3).**
Causa raíz aislada con `jshell` reproduciendo exactamente la lógica de
`SelfIssuedTokenKeys.decode()`: `openssl version` en esta máquina resuelve a **LibreSSL 2.8.3**
(la que trae macOS de fábrica como `/usr/bin/openssl`, no OpenSSL real). `openssl genpkey
-algorithm RSA -outform DER` bajo LibreSSL escribe un `RSAPrivateKey` PKCS1 crudo en vez de un
`PrivateKeyInfo` PKCS8 — confirmado con `openssl asn1parse` (el PKCS8 correcto anida un
`AlgorithmIdentifier` con el OID `rsaEncryption`; el resultado real solo tenía el `INTEGER` del
módulo en esa posición). `openssl pkey`/`asn1parse` autodetectan y aceptan ambos formatos sin
quejarse, por lo que cualquier comprobación manual con el propio CLI de `openssl` parecía
correcta; `KeyFactory("RSA").generatePrivate(new PKCS8EncodedKeySpec(...))` en Java no autodetecta
y rechaza PKCS1 sin ambigüedad ("algid parse error, not a sequence") — exactamente el error que
bloqueaba la primera verificación de D38-3, y que en un primer momento parecía un problema de
transmisión de variables de entorno hasta aislar la causa real con `jshell`. **Corrección:** la
clave generada se re-envuelve siempre explícitamente en PKCS8 real con
`openssl pkcs8 -topk8 -nocrypt`, sea cual sea el formato que `genpkey` haya producido — funciona
igual en LibreSSL y en OpenSSL real. Regeneradas las claves de prueba con el script corregido y
reverificadas: decodifican correctamente en Java, y la cadena completa de D38-3 quedó confirmada
con ellas.

**D38-5 — Ambos Dockerfile corrían como root (P2, corregido el del backend, el de frontend
documentado como riesgo aceptado).**
Escaneo real (`trivy config .`, no supuesto): `backend/Dockerfile` y `frontend/Dockerfile`
marcados por DS-0002 ("Specify at least 1 USER command... running as root can lead to a container
escape situation"). **Backend corregido**: añadido `addgroup`/`adduser` + `USER brika` antes del
`ENTRYPOINT` — el proceso no necesita root para nada (puerto 8081/8080, sin operación
privilegiada). Verificado con la imagen real construida y ejecutada: `docker exec ... whoami` →
`brika` (`uid=100`), y `/actuator/health` → `{"status":"UP"}` arrancando contra Postgres real, sin
ningún error de permisos. **Frontend (`nginx:1.27-alpine`) no se ha tocado**: la imagen oficial de
nginx ya hace su propio drop de privilegios internamente (el proceso maestro arranca como root
únicamente para poder enlazar el puerto 80 y gestionar los procesos worker, que sí corren como el
usuario `nginx` no-root vía la directiva `user nginx;` de su `nginx.conf` por defecto) — es el
patrón estándar y ampliamente aceptado de la imagen oficial, no una omisión. Forzar `USER nginx`
en el `Dockerfile` rompería el bind al puerto 80 sin cambios adicionales (capacidades Linux o
puerto no privilegiado) que no se pueden validar con el mismo rigor en esta sesión (sin bucle de
build+run+comprobación de la ruta HTTP real del frontend en contenedor). Documentado como deuda
técnica de bajo riesgo — el propio proceso maestro root de nginx es una superficie de ataque
mucho más pequeña y bien auditada que un JAR de aplicación propio corriendo como root.

## Adenda Sprint 39 — Cierre de release y validación reproducible

**Contexto:** Sprint 39 demuestra, con evidencia real y reproducible, que `main` está preparado
como release: CI real verde, build reproducible desde limpio, imágenes Docker válidas, JWT
persistente de extremo a extremo, y una decisión de readiness basada en evidencia.

**Gate 2 — CI real de `4491a09` (commit de cierre de Sprint 38), resultado confirmado.**
El propio informe de Sprint 38 se cerró con el workflow todavía `in_progress`. Comprobado ahora
con `gh run watch 32899319495 --exit-status` (espera controlada y acotada, no polling manual): el
run terminó con `conclusion: "success"` — **verde completo, los 3 jobs**:
- `Backend (build, format check, tests)`: ✓ en 6m40s (incluye `mvn verify` con Testcontainers y el
  format check de spotless).
- `Frontend (lint, build, tests)`: ✓ en 49s.
- `Backend Docker (build, healthcheck, security scan)`: ✓ en 2m19s — **incluye el paso "Trivy
  vulnerability scan", verde** — la prueba definitiva de que D38-1 (Sprint 38) está resuelto en CI
  real, no solo verificado en local: antes de ese fix, este job se saltaba (`needs: backend`, y
  `backend` fallaba) en cada uno de los últimos 5 pushes a `main`.

Única anotación: aviso de GitHub Actions sobre Node.js 20 deprecado en `actions/checkout@v4`/
`actions/setup-node@v4` (el runner los fuerza a Node.js 24 automáticamente) — advertencia de la
plataforma sobre las propias actions de terceros, no un fallo del pipeline ni algo bajo control de
este proyecto; no bloquea nada. Documentado como observación P3.

**Gate 3 — Auditoría del pipeline (`ci.yml`), sin discrepancias reales que corregir.**
Revisado línea por línea contra el comportamiento real: triggers (`push main` + `pull_request`),
`needs: backend` en `backend-docker` (correcto y ahora demostrado no-silencioso), servicios
declarados (`rabbitmq` en el job `backend` — necesario porque `NotificationAsyncIntegrationIT` usa
un broker real, no Testcontainers, por la propia limitación de RAM ya documentada en el comentario
del workflow; `postgres` en `backend-docker` — para el smoke-test de arranque de la imagen, no
relacionado con Testcontainers), caché de Maven/npm, propagación de fallos (`exit-code: "1"` en
Trivy). Ninguna discrepancia real encontrada entre lo que el YAML declara y lo que efectivamente
se ejecutó en el run verde de `4491a09`. **Único gap real observado (no una discrepancia, una
ausencia de cobertura):** no existe ningún job que construya y escanee la imagen `frontend`
(nginx) en CI — solo se construye y escanea `backend`. Añadir ese job sería ampliar el pipeline,
no corregir un defecto demostrado; documentado como mejora futura, no corregido en este sprint
(coherente con "no ampliar alcance").

**Gate 4 — Build reproducible desde limpio.** Entorno detenido y reiniciado desde cero
(`docker compose -f docs/docker-compose.yml up -d` con los 4 servicios sanos) antes de lanzar,
en paralelo, `mvn clean verify` (backend) y `npm ci && ng lint && ng test -- --watch=false`
(frontend), más `python3 -m unittest` (ai-worker). Resultados reales, frescos, no reutilizados:
- **ai-worker**: `Ran 24 tests in 22.322s` — **24/24 OK**.
- **Frontend**: `ng lint` limpio ("All files pass linting"); `npm ci` limpio (487 paquetes, 0
  vulnerabilidades); `ng test --watch=false` — **462/462 tests, 95/95 ficheros, exit code 0**
  (67.52s).
- **Backend**: `mvn clean verify` lanzado desde limpio contra Testcontainers reales (Postgres +
  MinIO, ya con el tag corregido de D39-1 — la ejecución incluye por tanto la validación de esa
  corrección) más el RabbitMQ real de docker-compose para `NotificationAsyncIntegrationIT`.
  Resultado real (`target/surefire-reports` + `target/failsafe-reports`, exit code 0): **105/105
  tests unitarios, 408/408 tests de integración, 0 failures, 0 errors, 0 skipped** (513/513
  total). Al final del log aparece una traza `PSQLException: Connection to localhost:... refused`
  y `[ERROR] Surefire is going to kill self fork JVM` — ambas ocurren **después** de que todos los
  tests ya hubieran pasado, durante el apagado del último fork/contenedor; es el mismo patrón de
  Ryuk-desactivado ya documentado en Sprint 36 (contenedores reciclados mientras Hikari intenta una
  validación de conexión de cierre), no una regresión nueva ni un test fallido.

**Gate 5 — Consistencia de versiones y dependencias.**
Comparado código real, Dockerfiles, CI y `docs/docker-compose.yml`:
- Java: `21` idéntico en `backend/pom.xml` (`<java.version>`), `.github/workflows/ci.yml`
  (`java-version: "21"`) y `backend/Dockerfile` (`maven:3.9-eclipse-temurin-21` /
  `eclipse-temurin:21-jre-alpine`). Sin discrepancia.
- Node: `22` idéntico en `frontend/Dockerfile` (`node:22-alpine`) y CI (`node-version: "22"`). El
  Node v24.19.0 de este shell local es del entorno interactivo del operador, no una versión
  declarada por el proyecto — no es una inconsistencia del repositorio.
- Angular: `@angular/core` `^22.1.0` vs `@angular/cli` `^22.1.4` — versiones compatibles dentro del
  mismo major/minor, sin contradicción.
- PostgreSQL (`postgres:16-alpine`) y RabbitMQ (`rabbitmq:3.13-management-alpine`): tags idénticos
  byte a byte entre `docs/docker-compose.yml` y los dos jobs de CI que los declaran. Sin
  discrepancia.
- **D39-1 (P2, corregido): MinIO — versión de test desincronizada de la versión real.**
  `docs/docker-compose.yml` fija el servicio `brika-minio` en
  `minio/minio:RELEASE.2025-09-07T16-13-09Z` (la versión que developers y prod usan realmente),
  pero los 10 ficheros de test que usan `MinIOContainer` (Testcontainers) estaban fijados en
  `minio/minio:RELEASE.2024-01-16T16-07-38Z` — una etiqueta ~20 meses más antigua. Esto significa
  que ninguna de las 10 IT que ejercitan almacenamiento (`DocumentServiceIT`, `DocumentEndpointsIT`,
  `EngagementContractEndpointsIT`, `ViabilityDossierEndpointsIT`,
  `AiDocumentExtractionEndpointsIT`, `AiExtractionCallbackEndpointsIT`,
  `HttpDispatchTransactionRaceIT`, `NotificationDocumentEventIT`, `PortalEndpointsIT`,
  `CrossModuleE2EIT`) valida realmente contra la versión de MinIO que corre en desarrollo/CI/prod.
  **Corrección** (proporcionada, mecánica, de bajo riesgo): las 10 referencias se han actualizado a
  `minio/minio:RELEASE.2025-09-07T16-13-09Z`, con un comentario explicativo añadido en
  `DocumentServiceIT.java` (fichero canónico del patrón `MinIOContainer`, referenciado por los
  demás). **Validación**: pendiente de confirmar con la ejecución completa de `mvn clean verify`
  del Gate 18 (tras esta corrección) — no se lanza una segunda ejecución completa de Testcontainers
  en paralelo con la del Gate 4 para no competir por los recursos limitados de la misma VM de
  colima (mismo criterio de precaución ya aplicado en Sprint 38 con Ollama/Trivy).
- `frontend/package.json` no declara `"engines"` y no hay una versión de Python documentada para
  `ai-worker` en `GETTING_STARTED.md`. Evaluado y descartado como hallazgo: ninguna de las dos
  ausencias produce una contradicción demostrable (no hay un sitio que afirme una versión distinta
  a la que realmente se usa) — solo la ausencia de un guardarraíl adicional, fuera del alcance de
  "corregir inconsistencias demostradas" (Regla 2, no ampliar alcance).

**Gate 6 — Build real de imágenes Docker (backend + frontend), desde limpio (`--no-cache`).**
- **Backend** (`brika-backend:sprint39`, 395MB): build limpio correcto. Contenedor real
  levantado contra el `brika-postgres` real de `docs/docker-compose.yml` (misma red
  `brika_default`, sin variables de self-auth — no es el objetivo de este gate, ver Gate 8):
  `id` dentro del contenedor → `uid=100(brika) gid=101(brika)` (mantiene el fix D38-5, non-root,
  re-verificado sobre la imagen recién construida, no reutilizada). `/actuator/health` → real
  `{"status":"UP","groups":["liveness","readiness"]}` tras arrancar y aplicar Flyway (26
  migraciones, "up to date"). Arranque real: **154s** hasta "Started BrikaApplication" — lento
  pero consistente con las limitaciones de esta VM de colima (1961 MB RAM) ya documentadas en
  sprints anteriores; no es un defecto del código ni de la imagen, solo del entorno local.
- **D39-2 (P2, corregido y re-verificado): frontend Docker corría como root sin justificación
  suficiente.** Sprint 38 dejó la imagen `frontend` sin `USER`, razonando que nginx "baja
  privilegios internamente" — cierto solo para los *worker processes*, no para el proceso maestro,
  que en la imagen oficial se queda en root únicamente para poder enlazar el puerto 80. Investigado
  empíricamente este sprint (no reaceptado a ciegas): concediendo la capability
  `cap_net_bind_service` al binario `nginx` (vía `setcap`, paquete `libcap`) y ajustando la
  propiedad de `/usr/share/nginx/html`, `/var/cache/nginx`, `/run` y `/etc/nginx/conf.d` a
  `nginx:nginx`, el proceso maestro puede enlazar el puerto 80 sin ser root. Aplicado a
  `frontend/Dockerfile` (ver comentario D39-2 en el propio fichero) y **validado sobre la imagen
  real recién construida** (`brika-frontend:sprint39`, 81.3MB, build limpio `--no-cache`): `id`
  dentro del contenedor → `uid=101(nginx)`; `ps aux` → **tanto el proceso maestro (PID 1) como los
  dos worker processes** se ejecutan como `nginx`, ninguno como root; `curl` real al puerto 80 →
  `HTTP 200`, contenido servido correctamente (37573 bytes). No fue necesario remapear el puerto ni
  tocar `docs/docker-compose.yml` ni la documentación — corrección pequeña, autocontenida y
  totalmente validada, tal como exige el gate.

**Gate 7 — Escaneo final de imágenes con Trivy (backend + frontend).**
*Nota de proceso: el sprint se pausó a mitad de este gate. Al reanudar se recuperó el estado real
con `git status`/`git diff`/`git diff --check` (limpio, sin sorpresas: exactamente los ficheros de
D39-1/D39-2 más los Dockerfiles) antes de aceptar nada como cerrado, tal como exige la regla de
preservación del trabajo.*

- **Backend — D39-3 (P1, corregido y re-verificado con dos iteraciones).** Primer escaneo del
  `brika-backend:sprint39` construido antes de la pausa: **CRITICAL 0, HIGH 3, MEDIUM 20** —
  `libexpat`/`p11-kit`/`p11-kit-trust` (HIGH) y `sqlite-libs` (MEDIUM) por detrás de las versiones
  ya publicadas en el mismo repo `v3.23` que la imagen base ya usa (`apk policy` lo confirma).
  Ninguno de los cuatro paquetes es invocado por el código de la aplicación (sin parseo XML vía
  expat, sin PKCS#11, sin SQLite — el único almacén es Postgres); se actualizaron igualmente porque
  el fix estaba trivialmente disponible. Corrección inicial: `apk upgrade --no-cache libexpat
  p11-kit p11-kit-trust sqlite-libs`. Reconstruida la imagen y re-escaneada: los cuatro paquetes
  desaparecieron del informe, pero apareció un **nuevo HIGH no relacionado**:
  `libssl3`/`libcrypto3`/`openssl` `CVE-2026-14456` (DoS por crecimiento de memoria no acotado en
  servidor QUIC). Causa raíz investigada explícitamente (regla del gate: "no cerrar sin analizar
  causa"): la base de datos de vulnerabilidades de Trivy se auto-actualizó entre el primer y el
  segundo escaneo (log: `[vulndb] Need to update DB` / descarga de 109 MiB) — el CVE es de
  divulgación reciente y no tiene relación con los paquetes tocados por la corrección; no es una
  regresión introducida por D39-3. No explotable en este contexto: esta app Java usa su propio
  stack TLS (JSSE), no llama a OpenSSL nativo, y no hay ningún servidor QUIC en esta imagen. Con
  fix ya disponible en el mismo repo confiable, se sustituyó la lista explícita por
  `apk upgrade --no-cache` sin argumentos (mismo criterio de riesgo mínimo, evita perseguir un
  objetivo móvil paquete a paquete). Reconstruida de nuevo y escaneada por tercera vez:
  **resultado final: capa Alpine 0 vulnerabilidades (CRITICAL 0, HIGH 0, MEDIUM 0, LOW 0); capa
  Java 6 MEDIUM, 0 HIGH, 0 CRITICAL** — los mismos 6 hallazgos jar (`jackson-databind`,
  `netty-codec-http`, `log4j-api`, `bouncycastle`) ya triados como no explotables en Sprint 38
  (D38-2), mismas librerías, mismas versiones ancladas en `pom.xml`. **Validación de ejecución real
  sobre la imagen final**: contenedor levantado contra `brika-postgres` real (red `brika_default`);
  `id` → `uid=100(brika)` (non-root mantenido); tras ~60s, `/actuator/health` real →
  `{"status":"UP","groups":["liveness","readiness"]}`; sin errores/excepciones nuevos en los logs
  atribuibles a la actualización de paquetes del sistema.

- **Frontend — D39-4 (P0, corregido y re-verificado).** Baseline real (imagen construida antes de
  aplicar el fix, con el mismo Trivy/misma base de datos): **CRITICAL 2, HIGH 33, MEDIUM 46, LOW
  26 (107 total)** — `libcrypto3`/`libssl3` (`CVE-2026-31789`, heap overflow en certificados X.509
  de 32-bit), más HIGH en `openssl`, `libxml2`, `musl`, `nghttp2-libs`, `libpng`, `libexpat`,
  `zlib`, `c-ares`, `curl`, todos ellos por detrás de versiones ya publicadas en el mismo repo
  `v3.21` que `nginx:1.27-alpine` ya usa (confirmado con `apk policy` paquete a paquete). Corregido
  con `apk update && apk upgrade --no-cache` antes de bajar privilegios (ver comentario D39-4 en
  `frontend/Dockerfile`). Reconstruida la imagen (`--no-cache`) y re-escaneada:
  **resultado: 0 vulnerabilidades de cualquier severidad (CRITICAL 0, HIGH 0, MEDIUM 0, LOW 0)** —
  de 107 hallazgos a 0. **Validación de ejecución real sobre la imagen final**: `id` →
  `uid=101(nginx)`; `ps aux` → proceso maestro (PID 1) y ambos worker processes como `nginx`, no
  root (el fix D39-2 se mantiene tras la actualización de paquetes); entrypoint de nginx completa
  limpio, incluyendo el paso IPv6 que en la validación de D39-2 quedaba con un aviso de
  "read-only filesystem" (ya no aparece); `curl` real → `HTTP 200`, 37573 bytes servidos
  correctamente. Ningún problema de permisos ni de directorios temporales introducido por la
  actualización de paquetes.

**Gate 7 — conclusión: CRITICAL 0, HIGH 0 en ambas imágenes reales, con evidencia empírica de
ejecución tras cada corrección.** MEDIUM restante (6, solo en el jar del backend) ya estaba
documentado y triado en Sprint 38; no se fuerza ninguna actualización de dependencias de aplicación
sin evidencia de mejora real, conforme a la Regla 2.

**Gate 8 — JWT persistente de extremo a extremo, re-validado desde limpio con el script
corregido.** Claves regeneradas con `scripts/generate-jwt-keys.sh` (con el fix D38-4 ya aplicado)
en un directorio temporal fuera del repo; verificación real de formato antes de usarlas —
reproducida la lógica exacta de `SelfIssuedTokenKeys.decode()` en `jshell`
(`Base64.getDecoder().decode(...)` → `KeyFactory("RSA").generatePrivate(new
PKCS8EncodedKeySpec(...))`) → `OK: RSA PKCS#8`, sin asumir que el CLI de `openssl` (que
autodetecta PKCS1/PKCS8) fuera suficiente prueba. Backend arrancado en real (jar de la build limpia
de este sprint, `java -jar`, perfil `local`, `brika.seed.enabled=true`) contra el Postgres real de
`docs/docker-compose.yml`, con `SELF_AUTH_INTERNAL_SIGNING_KEY_PEM`/`SELF_AUTH_PORTAL_SIGNING_KEY_PEM`
exportadas: **0 apariciones de "ephemeral" en el log de arranque** (antes de D38-3 aparecía
siempre). Login real (`POST /api/v1/auth/login`, `superadmin@brika.local`) → `accessToken` real
emitido; probado contra `GET /api/v1/companies` → `HTTP 200` con datos reales. **Reinicio completo
del proceso** (`kill` + nuevo `java -jar` desde cero, no un reload): arranque limpio, de nuevo 0
avisos de clave efímera. **Prueba definitiva**: el mismo `accessToken` emitido ANTES del reinicio,
usado DESPUÉS del reinicio contra `GET /api/v1/companies` → **`HTTP 200`, mismos datos reales** —
no solo "el aviso desapareció", sino persistencia de validación demostrada con un token real a
través de un reinicio real. Proceso detenido inmediatamente tras obtener la evidencia; ficheros de
claves temporales borrados de `/tmp`, nunca estuvieron en el repo (`git status` confirma ausencia
de cualquier rastro de `.secrets/` o claves).

**Gate 9 — Configuración de producción, auditada desde código real.**
`ProdEnvironmentValidator` (`EnvironmentPostProcessor`, fail-closed) revisado línea a línea contra
`application.yml`/`application-prod.yml` reales, buscando exactamente el patrón de D38-3
(documentación/variable/YAML/código desalineados).

- **D39-5 (P1, corregido y validado con tests reales): el validador fail-closed de PROD no cubría
  las credenciales de MinIO ni de RabbitMQ, y tampoco las de la base de datos.** A diferencia de
  las claves JWT/CORS/SMTP (que si faltan, `application.yml` las deja vacías y el validador las
  detecta), `brika.storage.access-key`/`secret-key` (`MINIO_ROOT_USER`/`PASSWORD`),
  `spring.rabbitmq.username`/`password` (`RABBITMQ_USER`/`PASSWORD`) y
  `spring.datasource.username`/`password` (`DB_USER`/`PASSWORD`) tienen en `application.yml` un
  valor por defecto **no vacío** (`brika`/`brika_dev_password`, el mismo par que usa
  `docs/docker-compose.yml` en local) — si un despliegue PROD real olvidase fijar esas seis
  variables de entorno, el backend arrancaría en silencio contra el object storage y el broker (y
  potencialmente la base de datos) con credenciales de desarrollo conocidas, sin que
  `ProdEnvironmentValidator` lo detectara, exactamente el tipo de riesgo fail-open que este
  validador existe para prevenir. **Corrección**: añadidas las 6 variables de entorno en crudo
  (`MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `DB_USER`,
  `DB_PASSWORD`) a la lista de comprobaciones obligatorias — comprobando la variable de entorno en
  crudo, no la propiedad Spring resuelta (mismo patrón ya usado para `SMTP_HOST`, por la misma
  razón: la propiedad resuelta nunca está vacía). **Validación real**:
  `ProdEnvironmentValidatorTest` ampliado con un test dedicado
  (`prodWithoutStorageBrokerOrDbCredentialsFailsEvenIfEverythingElseIsSet`) y actualizados los
  tests existentes que antes no configuraban estas variables; `mvn -Dtest=ProdEnvironmentValidatorTest
  test` → **7/7 tests, 0 failures, 0 errors** (ejecución real, no asumida).
- Resto de `application-prod.yml` revisado sin más discrepancias reales: `email-transport` fijado
  a `smtp` a nivel de perfil (no puede quedar en `noop` en PROD, ya cubierto); `seed.enabled: false`
  fijo; CORS exige valor no vacío sin comodín/localhost (ya cubierto). No se ha forzado ningún
  cambio adicional sin evidencia de un problema real, conforme a la Regla 2.

**Gate 10 — Sin secretos accidentales.** `git ls-files | grep -iE ".env$|.pem$|.key$|secret|
credential|.p12$|.pfx$"` → solo coincide con clases legítimas de aplicación cuyo nombre contiene
"Credential" (`PortalAccountCredentialRepository`, `UserCredentialService`, etc.), ningún fichero
de secreto real. `git diff --check` limpio. Búsqueda dirigida en el diff actual de patrones de
clave privada/AKIA/contraseña con valor real → sin hallazgos (solo los valores de prueba
`s3-password`/`db-password`/`broker-password` del test de D39-5). `.secrets/` confirmado en
`.gitignore`. Las claves JWT generadas para validar el Gate 8 se generaron fuera del repo
(`/tmp/jwt-sprint39-test`) y se borraron tras usarlas — nunca estuvieron en el árbol de trabajo.

**Gate 11 — Arranque reproducible del stack, desde parada real.** `docker compose down` real
(4 contenedores + red eliminados, confirmado por el log) seguido de `docker compose up -d` desde
cero: los 4 servicios (`brika-postgres`, `brika-rabbitmq`, `brika-mailpit`, `brika-storage`)
alcanzan `healthy` con comprobación acotada (RabbitMQ, el más lento, en ~8s adicionales, dentro de
un bucle acotado, no una espera indefinida).

**Gate 12/14 — Smoke test de negocio y generación documental, con HTTP real (sin mocks) contra un
backend real levantado desde la imagen `brika-backend:sprint39`.**
- Sin token → `401`. Login real (`superadmin@brika.local`) → `accessToken` real. Clientes → `200`.
  Empresas → `200`. Casos/expedientes → `200` (13 casos reales, datos de sprints anteriores
  persistidos en el volumen de Postgres — el `down`/`up` recicla contenedores, no borra datos).
  Tareas (`GET /api/v1/tasks`, tras corregir mi primer intento de ruta equivocada) → `200`, 4
  tareas reales. Documentos del caso → `200`, 3 documentos reales. Descarga: `GET
  /api/v1/documents/{id}/download` → URL presignada real de MinIO; descargada desde un contenedor
  en la misma red Docker que el backend (la firma SigV4 ata la URL al host `brika-storage:9000`
  original, así que la descarga se hizo desde dentro de la red, no reescribiendo el host) →
  contenido real recuperado (`<html><body><h1>DNI de prueba Sprint 36</h1></body></html>`),
  coincide con el fichero `dni-sprint36.html` esperado. Generación → almacenamiento → descarga
  demostrado con un flujo real de extremo a extremo. Logout con el payload correcto
  (`{"refreshToken":...}`) → `204`; el refresh token queda invalidado tras logout → intento
  posterior de refrescar con él → `401` (confirmado, no asumido).
- **Hallazgo menor (P3, documentado, no corregido — fuera de alcance de este sprint):** un primer
  intento de logout sin body (`POST /api/v1/auth/logout` sin JSON) devolvió `HTTP 500` en vez de
  `400` — `GlobalExceptionHandler` no tiene un `@ExceptionHandler` dedicado para
  `HttpMessageNotReadableException` (body ausente/malformado), así que cae en el catch-all
  genérico. Es un gap de contrato API preexistente en todos los endpoints con body obligatorio, no
  algo introducido por Sprint 39, y corregirlo tocaría el manejador de excepciones compartido de
  toda la API — más allá del alcance de "corrección mínima demostrada" de este sprint. Documentado
  como deuda técnica, no oculto.

**Gate 13 — RBAC y multi-tenant de regresión, con HTTP real.**
SUPERADMIN ve todas las empresas (comprobado en Gate 8); MANAGER autenticado ve **1 sola empresa**
(la suya) vía `GET /api/v1/companies` → `200`; MANAGER accediendo a un caso de **otro** tenant →
`404` (enmascarado, no `403`, patrón ya establecido); MANAGER accediendo a **su propio** caso →
`200`. BROKER intentando `POST /api/v1/users` (crear usuario, fuera de sus permisos) → `403`.
Sin token, cualquier endpoint protegido → `401`. Ningún resultado inesperado — sin regresión.

**Gate 15 — IA/Worker sin regresión.** `ai-worker` ya se ejecutó en fresco en el Gate 4
(`python3 -m unittest`, **24/24 OK**). Código de `_resolve_provider`/`AI_PROVIDER` revisado:
comportamiento `NO_PROVIDER` por defecto se mantiene (sin proveedor aprobado, no se intenta
inferencia real). Ningún fichero de `ai-worker/` aparece en el diff de este sprint — no se ha
tocado código de IA, así que no aplica repetir inferencia real de Ollama (>100s), conforme al
propio gate.

**Gate 16 — Documentación de despliegue vs. código real.** Revisados `docs/10_DEVOPS.md`,
`docs/GETTING_STARTED.md`, `docs/23_CLOUD_DEPLOYMENT_SPECIFICATION.md`, `.env.example`,
`scripts/generate-jwt-keys.sh` y los Dockerfiles contra el comportamiento real verificado en los
gates anteriores.
- **Contradicción real encontrada (introducida por la propia corrección D39-5 de este sprint, no
  preexistente):** `docs/10_DEVOPS.md` documentaba el conjunto fail-closed de PROD sin mencionar
  las nuevas variables `MINIO_ROOT_USER`/`MINIO_ROOT_PASSWORD`/`RABBITMQ_USER`/`RABBITMQ_PASSWORD`/
  `DB_USER`/`DB_PASSWORD`. **Corregido**: línea actualizada en `10_DEVOPS.md` para reflejar
  exactamente lo que `ProdEnvironmentValidator` valida ahora — exactamente el tipo de deriva
  documentación/código que este gate existe para cazar, en este caso generada por el propio sprint
  y cerrada en el mismo sprint.
- Ninguna referencia obsoleta encontrada sobre usuario root/no-root de las imágenes Docker ni sobre
  el puerto del frontend (`docs/10_DEVOPS.md`/`GETTING_STARTED.md`/`23_CLOUD_DEPLOYMENT_SPECIFICATION.md`
  no hacían ninguna afirmación al respecto que D39-2/D39-3/D39-4 pudieran haber dejado desfasada).
- `.env.example` ya documentaba `RABBITMQ_USER`/`PASSWORD`/`MINIO_ROOT_USER`/`PASSWORD` como
  valores de ejemplo de desarrollo (coherente); no se ha reescrito nada más sin evidencia de un
  problema real, conforme a la Regla 2.

**Gate 18 — Tests finales, números reales tras todos los cambios del sprint.**
Primer intento de `mvn clean verify` con el código final de D39-5 falló en `spotless:check` (mi
Javadoc no respetaba el formato exacto del auto-formateador — mismo patrón ya visto en Sprints 37 y
38); corregido con `mvn spotless:apply`, reconfirmado con `spotless:check` limpio, y **repetida la
ejecución completa desde limpio** (no se acepta el resultado del intento fallido):
- **Backend** (`mvn clean verify`, Testcontainers reales + RabbitMQ real de docker-compose):
  **106/106 tests unitarios, 408/408 tests de integración, 0 failures, 0 errors, 0 skipped**
  (514/514 total — 513 del Gate 4 + 1 nuevo test de D39-5), jar generado, `spotless:check` verde.
  Mismo ruido de apagado ya documentado (conexión Postgres cerrada durante el teardown del último
  fork, Ryuk desactivado) — no afecta al resultado.
- **Frontend**: `ng lint` → limpio. `ng test --watch=false` → **462/462 tests, 95/95 ficheros**,
  ejecución fresca (41.44s). `ng build` → compilación completa sin errores,
  `dist/frontend` generado.
- **AI Worker**: `python3 -m unittest tests.test_main` → **24/24 OK** (22.7s), ejecución fresca.

Ningún número reutilizado de gates anteriores; todos re-ejecutados tras el cierre de D39-1 a D39-5.

**Gate 19 — Git, commit y CI final.** Auditoría de `git diff`/`git status --porcelain` antes de
`add`: exactamente los 16 ficheros esperados (D39-1 a D39-5 más la propia adenda del decision log),
sin secretos, sin ficheros accidentales, sin untracked sorpresa. `mvn spotless:apply` aplicado antes
del commit para que el hand-written Javadoc de D39-5 pasase el formateador (mismo patrón que
Sprints 37/38).

Commit único `c18ddec` — `chore(v1): finalize release readiness and deployment validation`.
Confirmación explícita del usuario obtenida antes de `git push origin main` (mismo patrón de todo
el sprint). Push real: `4491a09..c18ddec main -> main`, `git status` tras el push → working tree
limpio, `main` y `origin/main` sincronizados en `c18ddec`.

CI real del commit final comprobada con `gh run watch 32955416199 --exit-status` (espera acotada,
no polling manual, mismo patrón del Gate 2) →
**`gh run view 32955416199 --json conclusion,status,headSha` → `{"conclusion":"success",
"headSha":"c18ddec...","status":"completed"}`** — verde real, no asumido:
- `Backend (build, format check, tests)`: ✓ 6m25s.
- `Frontend (lint, build, tests)`: ✓ 1m9s.
- `Backend Docker (build, healthcheck, security scan)`: ✓ 1m55s, incluyendo el paso "Trivy
  vulnerability scan" verde.

No se cierra el sprint con ningún workflow pendiente.

**Gate 17 — Decisión de readiness de Brikka V1.**
Evidencia acumulada, basada en ejecución real, no en optimismo:
- CI real verde del commit final (`c18ddec`, Gate 19): sí, los 3 jobs, incluido Trivy.
- CRITICAL=0, HIGH=0 en ambas imágenes Docker reales (Gate 7): sí, verificado con dos iteraciones
  de corrección + rescan hasta confirmarlo.
- Builds correctos desde limpio: sí (Gate 4/18, backend 514/514, frontend 462/462, ai-worker
  24/24, todo en fresco).
- Imágenes Docker validadas en ejecución real (no solo build): sí (Gate 6 — non-root backend y
  frontend, `/actuator/health` real, `HTTP 200` real).
- JWT persistente demostrado con un token real a través de un reinicio real: sí (Gate 8).
- Arranque reproducible desde parado: sí (Gate 11).
- Smoke tests de negocio correctos, sin mocks: sí (Gate 12/14 — login, clientes, empresas, casos,
  tareas, documentos, descarga presignada real, logout, protegido-sin-token).
- RBAC/multi-tenant sin regresión: sí (Gate 13).
- Sin P0/P1 bloqueante pendiente: los 5 hallazgos reales del sprint (D39-1 a D39-5) están todos
  corregidos y validados con evidencia empírica. Único pendiente es un hallazgo P3 documentado (el
  `HttpMessageNotReadableException` → 500 en vez de 400), explícitamente no bloqueante y fuera del
  alcance de este sprint por tocar el manejador de excepciones compartido de toda la API.

**Decisión: READY.** Brikka V1, en el commit `c18ddec`, cumple los criterios de readiness definidos
por este sprint con evidencia real en cada punto — no hay ningún P0, ningún P1 bloqueante, ningún
CRITICAL/HIGH sin resolver, ninguna inconsistencia de configuración de producción sin corregir, y
la CI real del commit final está verde.
