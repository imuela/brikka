# BRIKKA V2 — SEGUIMIENTO DE LA MIGRACIÓN (LEGACY → BRIKKA V2)

> Documento **vivo**. Se actualiza al cerrar cada tarea/bloque. El porcentaje refleja **trabajo real
> completado y validado** (backend + frontend + tests en verde), no estimaciones optimistas.
>
> Alcance: `BRIKKA_V2_MIGRATION_SCOPE.md` (I1–I5; I6 → FUTURO). La migración **termina** cuando I1–I5
> cumplen la condición de §7 de ese documento y se declara **MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA**.

---

## Barra de progreso global

```
V2  [██████████████████░░░░░░░░░░░░]   60 %   (3 / 5 bloques · I1 ✅ · I2 ✅ · I3 ✅)   —   siguiente: I4 (Sprint V2-4)
```

| Bloque | Peso | Estado | Backend | Frontend | Tests BE | Tests FE | % bloque |
|---|---:|---|---|---|---|---|---:|
| **I1** · Checklist documental | 25 % | ✅ Completado | ✅ | ✅ | ✅ | ✅ | 100 % |
| **I2** · Scoring de fábrica + RAG | 20 % | ✅ Completado | ✅ | ✅ | ✅ | ✅ | 100 % |
| **I3** · Precondiciones de transición | 15 % | ✅ Completado | ✅ | ✅ | ✅ | ✅ | 100 % |
| **I4** · Simulación enriquecida | 25 % | ⬜ Pendiente | ⬜ | ⬜ | ⬜ | ⬜ | 0 % |
| **I5** · Dossier + ZIP + narrativa | 15 % | ⬜ Pendiente | ⬜ | ⬜ | ⬜ | ⬜ | 0 % |
| **TOTAL** | 100 % | | | | | | **60 %** |

Leyenda: ⬜ pendiente · 🟨 en curso · ✅ completado y validado.

---

## Bloque / sprint actual

**V2-4 · I4 · Simulación hipotecaria enriquecida** (siguiente, sin empezar).

### V2-3 · I2 · Scoring de fábrica + indicador RAG — ✅ CERRADO (2026-08-31)

Commit del sprint: pendiente de `git commit` (ver "Registro de avance"). Implementado:

**1. Scoring de fábrica — Migración `V29__seed_default_scoring_ruleset.sql`** (sin tabla nueva, sin
permiso nuevo). Usa el **motor existente** (`ScoringEngine` / `ScoringService`, ADR-SCORING-001), no
un segundo sistema:

- 1 `scoring_rulesets` `status = ACTIVE`, `code = default-operation-v1`, `version = v1`.
- **Categorías en el jsonb del ruleset** (`rules.categories`), no en Java:
  `RED maxScore 40` · `AMBER maxScore 69` · `GREEN maxScore null` (catch-all). El total acumula
  "puntos a favor" → menos señales favorables ⇒ categoría más baja.
- **4 reglas** `scoring_rules` (solo los 5 campos cerrados de `ScoreField`):

  | code | condición | peso |
  |---|---|---:|
  | `ltv-strong` | `computed.ltv <= 0.70` | 50 |
  | `ltv-moderate` | `computed.ltv <= 0.80` | 25 |
  | `term-standard` | `financingRequest.termMonths <= 360` | 20 |
  | `amount-known` | `financingRequest.requestedAmount > 0` | 5 |

- Datos pasan `ScoringRulesValidator`. Reproducible: migración versionada (una vez por BD, igual que
  V27/V28), `code`/`version` fijados. Un expediente sin inmueble ni solicitud → 0 → `RED`.
- **NO** fórmula Legacy de scoring de cliente, **NO** 65/35, **NO** gate numérico de transición.

**2. Indicador RAG del expediente — `scoring/CaseRagService`** (decisión §10.1: cualitativo,
determinista, sin variables de negocio nuevas, sin IA). Combina 3 ejes, cada uno **filtrado por
`company_id`**:

| Eje (`axis`) | Fuente | Regla |
|---|---|---|
| `scoring` | último `scoring_results` del caso (repo `ORDER BY calculated_at DESC`) | categoría `GREEN`/`AMBER`/`RED` → mismo nivel; otra categoría (ruleset a medida) → `NOT_EVALUATED`; sin resultado → `NOT_EVALUATED` |
| `viability` | `case_financial_analysis_results`, último por cliente | `FAVORABLE→GREEN`, `REVISAR→AMBER`, `NO_VIABLE→RED`; **peor** entre titulares; sin análisis → `NOT_EVALUATED` |
| `documentation` | `CaseChecklistService.checklist(...)` (I1) | obligatorio completo → `GREEN`; 0 aprobados de N → `RED`; parcial → `AMBER`; sin requisitos (p. ej. `MORTGAGE`/`REFINANCE`) → `NOT_EVALUATED` |

- **Combinado = peor de los ejes evaluados** (severidad `RED > AMBER > GREEN`); `NOT_EVALUATED` no
  empeora; **todos `NOT_EVALUATED` ⇒ `NOT_EVALUATED`** (nunca degrada a verde).
- Determinista: mismos datos almacenados ⇒ mismo indicador (test dedicado).
- `scoring` package: solo **lee** de `financialanalysis` y `document` (ninguno importa `scoring`) →
  sin ciclo de paquetes.

**3. API — `GET /api/v1/cases/{caseId}/scoring/rag`** (`scoring/web/CaseRagController` +
`CaseRagResponse`). Convenciones actuales: `CaseAccessService.requireCaseAccess(auth, "SCORING_READ",
caseId)` (tenant + rol + asignación; caso de otro tenant → **404** antes de tocar datos RAG).
**Permiso reutilizado `SCORING_READ`** (ya sembrado a SUPERADMIN/MANAGER/BROKER) — **sin permiso
nuevo, sin endpoint de administración nuevo**. Contrato de error intacto (`{code,message,requestId}`).
Respuesta: `{ rag, axes:[{axis, level, detail}] }` (enums viajan por nombre; etiquetas en el front).

**4. Frontend** (mínimo, solo visualización del RAG en `case-detail`):
- `features/scoring/scoring.model.ts` + `scoring.service.ts` (`getRag` + `run` reutiliza
  `POST /scoring/run` — sin endpoint nuevo).
- `case-detail`: sección **"Indicador RAG"** (`*appHasPermission="'SCORING_READ'"`) — badge del
  indicador global + tabla de los 3 ejes (señal / nivel / detalle). Botón **"Calcular scoring"**
  (`*appHasPermission="'SCORING_RUN'"`) → `POST /scoring/run` → recarga el RAG.
- `status-labels.ts`: `RAG_LEVEL_LABELS` (Verde/Ámbar/Rojo/Sin evaluar) + `RAG_AXIS_LABELS`.
- `status-tone.ts`: `GREEN→success`, `AMBER→warning`, `RED→error` (conjunto cerrado, antes de la
  heurística léxica); `NOT_EVALUATED→neutral`.
- `error-messages.ts`: +`NO_ACTIVE_SCORING_RULESET` (único fallo que puede surgir del botón, si se
  desactivara el ruleset de fábrica).
- **`case-list` → FUTURO** (`SCOPE §6.5`): el semáforo por fila exigiría N peticiones o un endpoint
  de lote; no se justifica en I2. Sin otros cambios de componente.

**Tests (backend, todos verdes):**
- `CaseRagServiceIT` (nuevo, Postgres) — **7/7**: sin señales → `NOT_EVALUATED`; todo favorable →
  `GREEN`; peor eje manda (`GREEN`+`REVISAR` → `AMBER`); `NO_VIABLE` fuerza `RED` pese a scoring
  `GREEN`; documentación obligatoria sin aprobar → eje `RED`; **datos de otra empresa nunca entran**
  (scoring + viabilidad de otro `company_id` → ejes `NOT_EVALUATED`); determinista (dos llamadas ⇒
  igual).
- `ScoringEndpointsIT` (+2) — vía HTTP: `GET /scoring/rag` legible por el equipo del caso, eje
  `scoring` refleja el ruleset de fábrica sin crear ninguno (LTV 0.60 → `GREEN`); caso de otro
  tenant → **404**.
- `ScoringNoActiveRulesetIT` — **adaptado**: desactiva el ruleset de fábrica (`UPDATE ... SET status
  = 'INACTIVE'`) y sigue verificando el guard `NO_ACTIVE_SCORING_RULESET`.
- `FlywayMigrationIT` — 28→29 migraciones; +asserts V29 (1 ruleset `ACTIVE`, 4 reglas, 3 categorías).
  Sin cambios en contadores de permisos/roles (V29 no añade ninguno).
- Suite scoring existente (`ScoringEndpointsIT`, `ScoringRulesetEndpointsIT`, `ScoringEngineTest`,
  `ScoringRulesValidatorTest`, `CrossModuleE2EIT`) — sin cambios de aserción necesarios (todas
  localizan sus resultados por `rulesetId`; ninguna asume cero rulesets).

**Tests (frontend, todos verdes):** `scoring.service.spec` (nuevo, `getRag`/`run`),
`status-tone.spec` (nuevo, mapeo RAG + patrones existentes), `case-detail.component.spec` (+3:
sección RAG gateada por `SCORING_READ` y render de ejes; botón "Calcular scoring" solo con
`SCORING_RUN` + recarga; error del run). `ng test` **499/499** · `ng lint` verde.

**FUTURO (detectado, no implementado):** indicador RAG en `case-list` (registrado en `SCOPE §6.5`).

### V2-2 · I3 · Precondiciones de transición — ✅ CERRADO (2026-08-31)

Commit del sprint: pendiente de `git commit` (ver "Registro de avance"). Implementado:

**Tres gates** (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §5), validados en el **backend** (autoridad),
en `CaseTransitionPreconditions` (colaborador nuevo en `casemgmt`, invocado por
`CaseService.changeStatus`):

| Transición | Precondición | Código de error (400, `{code,message,requestId}`) |
|---|---|---|
| `DOCUMENTATION → ANALYSIS` | checklist documental **obligatorio APROBADO** (usa I1: `CaseChecklistService.checklist(...).complete()`) | `PRECONDITION_CHECKLIST_INCOMPLETE` |
| `BANK_SEARCH → BANK_SUBMISSION` | ≥1 `bank_request` del caso **de la misma empresa** (`BankRequestRepository.existsByCaseIdAndCompanyId`) | `PRECONDITION_NO_BANK_REQUEST` |
| `OFFER → FORMALIZATION` | `final_financing` del caso, misma empresa, cuya `bank_offer_id` pertenece a una oferta del caso | `PRECONDITION_NO_SELECTED_OFFER` |

- **Ausencia de checklist** (tipo de operación sin requisitos sembrados, p. ej. `MORTGAGE`): el
  checklist queda vacío → `complete = true` → gate 1 no bloquea (comportamiento seguro y explícito).
- **Sin gates adicionales.** Ninguna otra transición se toca. `reopen` intacto.

**Excepción autorizada** (mecanismos existentes, sin auditoría paralela):
- Permiso nuevo `CASE_TRANSITION_OVERRIDE` (`V28`), sembrado a **MANAGER + SUPERADMIN** (mismo
  criterio que `BANK_MATCHING_OVERRIDE`).
- `ChangeCaseStatusApiRequest.override` (opcional, por defecto `false`). Si `true`:
  `CaseController` exige el permiso (`403` sin él, vía `authorizationService.requirePermission`);
  `CaseService` exige un **motivo no vacío** (`PRECONDITION_OVERRIDE_REASON_REQUIRED`) y **salta**
  los gates.
- Registro: el motivo se persiste en `case_status_history.reason` con el marcador
  `[PRECONDITION_OVERRIDE] `; el evento de auditoría `CASE_STATUS_CHANGED` incluye `"override":true`.

**Frontend** (mínimo, sin rediseño):
- `change-status-dialog`: casilla **"Forzar la transición (excepción autorizada)"**, visible solo
  si el usuario tiene `CASE_TRANSITION_OVERRIDE` (`SessionStore.hasPermission`); si se marca, el
  motivo pasa a obligatorio (validado en cliente y en servidor); envía `override` en el body.
- `error-messages.ts`: traducción en español de los 4 códigos de gate (única fuente de verdad de
  traducción de errores; no se cambia el contrato).
- `case.model.ts`: `ChangeCaseStatusRequest.override?`.

**Migración `V28__case_transition_override_permission.sql`**: 1 permiso + 2 `role_permissions`. Sin
tabla nueva.

**Tests (backend, todos verdes):**
- `CaseTransitionPreconditionsIT` (nuevo, Postgres + MinIO) — **15/15**: gate 1 (pendiente / subido
  no aprobado / todo aprobado / override / sin requisitos), gate 2 (0 solicitudes / ≥1 / otra
  empresa / override), gate 3 (sin oferta / con oferta del caso / oferta de otra empresa /
  override), override sin motivo → rechazado, transiciones no gateadas intactas.
- `CrmCaseEndpointsIT` (+2) — vía HTTP: gate 2 bloquea `400 PRECONDITION_NO_BANK_REQUEST`; MANAGER
  fuerza con `override:true` → `200` + evento de auditoría con `"override":true`; BROKER (con
  asignación, sin el permiso) → `403`.
- `CaseServiceIT.fullHappyPath` — actualizado con `bank_request` + `final_financing` reales para
  pasar los gates 2/3; sigue verde (17/17).
- `CaseChecklistServiceIT` (I1) — un test de idempotencia usa ahora `override` para el hop
  `DOCUMENTATION→ANALYSIS`; verde (6/6).
- Contadores de RBAC actualizados: `FlywayMigrationIT` (27→28 migr., 116→117 permisos, 250→252
  role_permissions), `RbacSeedIT` (252 / 117 / 92 / breakdown MANAGER 82, SUPERADMIN 92),
  `IdentityEndpointsIT` (`/me/permissions` MANAGER 81→82).

**Tests (frontend, todos verdes):** `change-status-dialog.component.spec` — casilla oculta sin
permiso, visible y envía `override:true` con motivo, motivo obligatorio al forzar, traducción del
error de gate. `npm test` **492/492**; `ng lint` + `npm run build` verdes.

**FUTURO (detectado, no implementado):** al cambiar el estado de un caso desde `case-detail`, la
sección "Checklist documental" no se recarga automáticamente (solo se recarga tras acciones
documentales — enganche de I1). Cosmético; se registra en `SCOPE §6.5`.

### V2-1 · I1 · Checklist documental — ✅ CERRADO (2026-08-31)

Commit del sprint: pendiente de `git commit` (ver "Registro de avance"). Implementado:

- **Migración `V27__document_checklist.sql`**: `UNIQUE(operation_type, document_type_id)` en
  `document_requirements`; `documents.client_id uuid NULL` + índice; seed de 9 requisitos para
  `operation_type='PURCHASE'` (mapa Legacy → códigos `V2`; 5 obligatorios / 4 opcionales;
  `conditions.appliesTo` = `PER_HOLDER` | `PER_CASE`).
- **Backend nuevo:** `ChecklistItemState`, `CaseChecklistItem`, `CaseChecklist`,
  `CaseChecklistService` (auto-gen idempotente + vista + reconciliación), `DocumentRequestFulfillment`
  (hook de revisión, AI-ready), `CaseChecklistController` + `CaseChecklistResponse`
  (`GET /api/v1/cases/{caseId}/checklist`, permiso `DOCUMENT_REQUEST`).
- **Backend modificado:** `Document`/`DocumentRepository` (+`client_id`, overloads compatibles),
  `DocumentService` (`createDocument` con clientId + hook en `review`), `DocumentController` +
  `CreateDocumentApiRequest` (+`clientId` opcional, validado contra `case_clients`),
  `DocumentResponse` (+`clientId`), `DocumentRequirementRepository` (`findActiveByOperationType`),
  `DocumentRequestRepository` (`existsForRequirement`, `findByCaseTypeAndClientForRequirements`),
  `CaseService.changeStatus` (auto-gen al entrar en `DOCUMENTATION`, idempotente en la vuelta
  `ANALYSIS→DOCUMENTATION`).
- **Frontend:** `document.model.ts` (+`CaseChecklist`/`Item`, `clientId?` en `CreateCaseDocumentRequest`),
  `documents.service.ts` (`getChecklist`), `case-detail` (sección "Checklist documental" con resumen
  "Faltan N obligatorios" + tabla documento/titular/obligatorio/estado; recarga tras cada acción
  documental), `create-document-dialog` (selector "Titular (opcional)" cuando el caso tiene
  titulares).
- **Cierre por evidencia (decisión §10.3):** un requisito solo cuenta como completo con
  `review_status = APPROVED`; `SUBIDO` visible como estado intermedio; `REJECTED` de una versión
  previamente aprobada reabre el requisito.
- **AI-ready:** el casado "documento → requisito" vive en `DocumentRequestFulfillment` (colaborador
  aislado), no en el controlador ni el formulario. **Sin IA implementada.**

**Tests (todos verdes):**
- `CaseChecklistServiceIT` (nuevo) — 6/6: auto-gen per-holder/per-case + idempotencia · checklist
  todo MISSING/incompleto · subir ≠ completo (request sigue PENDING) · aprobar → APPROVED + request
  FULFILLED + `complete=true` · rechazar versión aprobada → REJECTED + request PENDING · aislamiento
  de tenant.
- `DocumentEndpointsIT` (+2) — 16/16: `GET /checklist` devuelve items y `complete=false`; otra
  empresa → 404.
- `FlywayMigrationIT` — actualizado (26→27 migraciones, +asserts V27) y verde.
- Barrido de regresión (background): **document, casemgmt, contract, dossier, financialanalysis,
  ai, e2e, portal, notification (sync + async)** — todos verdes. `NotificationAsyncIntegrationIT`
  requiere el broker `brika-rabbitmq` real (documentado en `12_DECISION_LOG.md`, no Testcontainers);
  levantado con `docker compose -f docs/docker-compose.yml up -d rabbitmq` → 4/4 verde. Sin relación
  con I1.
- Frontend — `npm test` **488/488** (+3: `documents.service` getChecklist, `create-document-dialog`
  holders, `case-detail` render de checklist); `ng lint` y `npm run build` verdes.

### V2-0 · Preparación — ✅ CERRADO (2026-08-31)

- [x] Alcance §1–§10 de `BRIKKA_V2_MIGRATION_SCOPE.md` congelado con las decisiones del propietario.
- [x] Trabajo Sprint 40.x (4 archivos `client-form` / `ClientFinancialProfile`) commiteado
      **independiente** en `main` → `5c4b223`. No modificado, no revertido. (Incidencia I-01 cerrada.)
- [x] Rama `feat/v2-migration` creada desde `main@5c4b223`.
- [x] Documentos de planificación V2 (`SCOPE`, `FUNCTIONAL_GAP`, `BUSINESS_RULES_GAP`, este
      `PROGRESS`) commiteados en la rama.
- [x] **Baseline de tests** (ver tabla "Tests — estado"):
  - Frontend: `ng lint` ✅ · `npm test` **485/485** (97 ficheros) ✅.
  - Backend unit (Surefire, sin Docker): **106/106** ✅ (`TestProfileConfigIT` es un IT que
    Surefire recoge; requiere Docker; verde bajo Colima).
  - Backend integración (Failsafe, Colima): `FlywayMigrationIT` + `CaseServiceIT` = **26/26** ✅;
    migraciones V1–V26 aplican limpias.
  - **No** se corrió la suite completa `./mvnw verify` (54 ITs, ~15–20 min, flaky en esta máquina
    por Testcontainers/Colima — memoria del proyecto). Se ejecutará por dominio afectado al cerrar
    cada sprint y **completa** antes de declarar la migración terminada.
  - Entorno Testcontainers: `DOCKER_HOST=unix://$HOME/.colima/default/docker.sock`,
    `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock`, `TESTCONTAINERS_RYUK_DISABLED=true`
    (documentado en `GETTING_STARTED.md`).

Luz verde para **V2-1 (I1)**.

---

## Detalle por bloque

### I1 · Checklist documental  *(P0 — Sprint V2-1 · ✅ COMPLETADO 2026-08-31)*

**Diseño (decisiones de sprint, tras leer el dominio `document` real):**

1. **Per-titular vs per-expediente** se modela en `document_requirements.conditions` (jsonb ya
   existente, hoy sin semántica): `{"appliesTo":"PER_HOLDER"}` | `{"appliesTo":"PER_CASE"}`
   (por defecto `PER_CASE` si falta).
2. **`documents` es hoy `(case_id, document_type_id)` sin dimensión de cliente** (Legacy tenía
   `client_documents` + `case_documents` separadas). Para poder **cerrar por evidencia por titular**
   —que está dentro del DoD de I1— se añade `documents.client_id uuid NULL REFERENCES clients(id)`
   (cambio arquitectónico mínimo y necesario, `SCOPE §8`). Flujos existentes pasan `null`
   (comportamiento intacto). `create-document-dialog` gana un selector "Titular" **opcional**
   (visible solo si el caso tiene titulares).
3. **Auto-generación:** hook en `CaseService.changeStatus` cuando `newStatus == DOCUMENTATION` →
   `CaseChecklistService.ensureRequests(...)`: por cada `document_requirement` activo de la
   `operationType`, crea `document_requests` idempotentes — `PER_CASE` → 1 request
   (`requested_from_client_id = null`); `PER_HOLDER` → 1 request por `case_clients` con
   `participation_type IN (HOLDER, CO_HOLDER)`. Nunca duplica; nunca borra requests si el catálogo
   cambia luego.
4. **Estado del requisito** (derivado, no columna nueva en `document_requests`):
   `APROBADO` si ∃ `Document(case, type, client_id = request.client)` con `status = APPROVED`;
   `SUBIDO` si existe versión pero `status = PENDING`; `RECHAZADO` si `status = REJECTED`;
   `FALTA` si no hay documento. **Un requisito solo cuenta como completo con `APPROVED`** (decisión
   del propietario §10.3).
5. **Reconciliación de `document_requests.status`:** hook en `DocumentService.review` —
   `APPROVED` → requests casantes `PENDING→FULFILLED`; `REJECTED` → `FULFILLED→PENDING`. Defensa en
   profundidad: `CaseChecklistService` también reconcilia al leer.
6. **Endpoint:** `GET /api/v1/cases/{caseId}/checklist` → `{ mandatoryMissing, mandatoryTotal,
   optionalMissing, optionalTotal, complete, items[] }`. Permiso: reutiliza `DOCUMENT_REQUEST` (no
   se añade permiso nuevo — RBAC estable, `ADR-RBAC-001`). `complete` (todos los obligatorios
   `APPROVED`) es lo que consumirá **I3**.
7. **AI-ready:** el casado "documento → requisito (tipo + titular)" vive en un colaborador
   dedicado (`DocumentRequestFulfillment`), no en el controlador ni en el formulario — punto de
   extensión para que una clasificación IA lo proponga en el futuro.
8. **Migración `V27__document_checklist.sql`:** seed de `document_requirements` para `PURCHASE`
   (mapa Legacy → códigos `V2`), `ALTER TABLE documents ADD client_id`, índice, y
   `UNIQUE(operation_type, document_type_id)` en `document_requirements` (guarda la auto-gen).

**Definition of Done:**
- [x] Migración `V27__seed_document_requirements.sql` con el mapa `PURCHASE` (por titular:
      `DNI`, `PAYSLIP`, `EMPLOYMENT_HISTORY` obl.; `INCOME_TAX_RETURN`, `EMPLOYMENT_CONTRACT`,
      `BANK_STATEMENT` opc. — de expediente: `LAND_REGISTRY_EXTRACT`, `DEPOSIT_CONTRACT` obl.;
      `PROPERTY_APPRAISAL` opc.).
- [x] Auto-generación idempotente de `document_requests` al entrar el caso en `DOCUMENTATION`
      (por titular vs de expediente).
- [x] Cierre del requisito **solo** con `review_status = APPROVED` de la versión casante; estado
      intermedio `SUBIDO` visible.
- [x] `CaseChecklistService` + endpoint de completitud (obligatorios/opcionales, por titular y de
      expediente, con estado por requisito).
- [x] Vista de checklist en `case-detail` (Angular) integrada con guards y manejo de errores.
- [x] Punto de extensión "documento → requisito (tipo + titular)" como interfaz (AI-ready, sin IA).
- [x] Tests BE: auto-gen idempotente · requisito NO se cierra al subir, SÍ al aprobar · aislamiento
      de tenant.
- [x] Tests FE: componente de checklist.
- [x] Batería completa en verde.
- [x] `docs/` y este PROGRESS actualizados.

**Criterio de aceptación:** al pasar un caso `PURCHASE` a `DOCUMENTATION` aparecen los requisitos;
subir el documento del tipo correcto → requisito `SUBIDO`; aprobarlo → `APROBADO`; la vista muestra
"faltan N obligatorios".

### I2 · Scoring de fábrica + indicador RAG  *(P1 — Sprint V2-3 · ✅ COMPLETADO 2026-08-31)*

**Definition of Done:**
- [x] Migración `V29__seed_default_scoring_ruleset.sql`: `scoring_rulesets` `ACTIVE`
      (`default-operation-v1` / `v1`) + 4 reglas (`ltv-strong`, `ltv-moderate`, `term-standard`,
      `amount-known`, solo campos de `ScoreField`) + 3 categorías **`GREEN`/`AMBER`/`RED`**
      (`maxScore` en el jsonb del ruleset: `RED 40` / `AMBER 69` / `GREEN null`). Usa el motor
      existente; configuración por datos, no hardcoded en Java.
- [x] `scoring/CaseRagService` + `GET /api/v1/cases/{caseId}/scoring/rag` (solo lectura, sin
      persistencia nueva, permiso reutilizado `SCORING_READ`): RAG = **peor de los ejes evaluados**
      de {categoría del último `scoring_results`, peor viabilidad por cliente de
      `case_financial_analysis_results`, completitud del checklist obligatorio (I1)}. Cada eje
      filtra por `company_id`. Ejes ausentes → `NOT_EVALUATED`; todos ausentes → `NOT_EVALUATED`.
- [x] Badge en `case-detail` (indicador global + tabla de 3 ejes). **`case-list` → FUTURO** (`SCOPE
      §6.5`: exigiría N peticiones o endpoint de lote).
- [x] Tests BE: seed del ruleset (`ACTIVE`, 3 categorías, 4 reglas, reproducible — `FlywayMigrationIT`);
      scoring evalúa con el ruleset sembrado y comportamiento sin ruleset activo
      (`ScoringNoActiveRulesetIT` adaptado); combinación RAG completa/parcial/ausente
      (`CaseRagServiceIT` 7/7); aislamiento de tenant (`GET /scoring/rag` de otro tenant → 404;
      datos de otra empresa nunca entran); regresión de scoring/viabilidad.
- [x] Tests FE: `scoring.service.spec`, `status-tone.spec`, sección RAG en `case-detail.component.spec`.
- [x] Batería completa en verde.
- [x] `docs/` (`SCOPE §1 I2` + `§6.5`) y este PROGRESS actualizados.

**Criterio de aceptación:** LTV bajo + viabilidad FAVORABLE → verde; cualquier eje en rojo → rojo;
sin datos → "sin evaluar".  ✅ verificado en `CaseRagServiceIT` + `ScoringEndpointsIT`.

### I3 · Precondiciones de transición  *(P1 — Sprint V2-2 · ✅ COMPLETADO 2026-08-31)*

**Definition of Done:**
- [x] `DOCUMENTATION → ANALYSIS`: exige checklist documental **obligatorio aprobado** (I1).
- [x] `BANK_SEARCH → BANK_SUBMISSION`: exige ≥1 solicitud/relación bancaria válida (`bank_requests`).
- [x] `OFFER → FORMALIZATION`: exige oferta seleccionada (`final_financing` → `bank_offer_id`).
- [x] Excepción autorizada: permiso específico + **motivo obligatorio** en `case_status_history.reason`.
- [x] Sin gate numérico de score. Sin gates adicionales.
- [x] Tests BE por transición: bloqueada / permitida / con excepción · error estructurado
      `{code, message, requestId}`.
- [x] `change-status-dialog` (Angular) refleja el motivo de bloqueo y la opción de excepción.
- [x] Batería completa en verde.

**Criterio de aceptación:** no se avanza si la precondición falla; con permiso + motivo sí, y queda
auditado.

### I4 · Simulación enriquecida  *(P1 — Sprint V2-4)*

**Definition of Done:**
- [ ] Migración `V30__simulation_interest_type_and_bonifications.sql` (antes V29; V29 la ocupa I2): `interest_type`
      (`FIXED`/`VARIABLE`/`MIXED`), desglose (`spread`, `euribor`, `fixed_years`, `fixed_rate`,
      `variable_spread`), `base_interest_rate` / `final_interest_rate` `numeric(7,4)`, bonificaciones
      (tabla o `metadata` tipado), `ico_guarantee`.
- [ ] `final_interest_rate = max(0, base − Σ tasas de bonificaciones activas)` **en el backend**.
- [ ] `estimated_payment` recalculado con `MortgagePaymentCalculator` a partir de `final_interest_rate`.
- [ ] Validación `MIXED`: años fijos < plazo total.
- [ ] `create-simulation-dialog` (Angular): tipo de interés, desglose, bonificaciones, ICO; muestra
      base / final / cuota.
- [ ] Tests BE: `final_interest_rate` con varias bonificaciones (floor 0) · cuota francesa ·
      validación MIXED · redondeo `numeric(7,4)` / `numeric(14,2)`.
- [ ] Tests FE: diálogo.
- [ ] Batería completa en verde.

**Criterio de aceptación:** una simulación `VARIABLE` con euríbor + diferencial + 2 bonificaciones
activas muestra base, final y cuota coherentes; el dossier lo refleja.

### I5 · Dossier + ZIP documental + narrativa determinista  *(P1 — Sprint V2-5)*

**Definition of Done:**
- [ ] `CaseDocumentsArchiveService` + endpoint: ZIP **en streaming** con la última versión de cada
      `Document` del caso, organizado por tipo/titular, con `DOCUMENT_DOWNLOAD` + tenant por
      documento. Sin fichero temporal.
- [ ] `ViabilityDossierService` combina/enlaza dossier HTML + ZIP.
- [ ] `NarrativeService` (Java, reglas, sin IA): nº de titulares, homogeneidad laboral, media de
      antigüedad e ingresos, comparación precio/valor. Estrategia sustituible (AI-ready).
- [ ] Botón "Descargar toda la documentación" en `case-detail` / `viability-dossier`.
- [ ] Tests BE: contenido y estructura del ZIP · permisos y tenant · caso sin documentos · narrativa
      por rama (1 titular / varios; antigüedad; ingresos).
- [ ] Tests FE: acción de descarga.
- [ ] Batería completa en verde.

**Criterio de aceptación:** el broker descarga un ZIP con todos los documentos del caso; el dossier
incluye un párrafo de contexto generado por reglas.

---

## Tests — estado

| Ámbito | Baseline (V2-0) | Actual (tras I2) | Nuevos en V2 |
|---|---|---|---|
| Frontend (`ng lint` + `npm test`) | lint ✅ · **485/485** | lint ✅ · **499/499** | +14 |
| Backend unit (Surefire, sin Docker) | **106/106** ✅ | **106/106** ✅ | 0 |
| Backend IT — barrido I1 (document, casemgmt, contract, dossier, financialanalysis, ai, e2e, portal, notification) | — | **verde** (189 tests) | I1 |
| Backend IT — barrido I3 (`CaseTransitionPreconditionsIT` 15, `CrmCaseEndpointsIT` 23, `CaseServiceIT` 17, `CaseChecklistServiceIT` 6, `FlywayMigrationIT`, `RbacSeedIT` 30, `IdentityEndpointsIT`) | — | **verde** | `CaseTransitionPreconditionsIT` 15/15 · `CrmCaseEndpointsIT` +2 |
| Backend IT — barrido I2 (`CaseRagServiceIT` 7, `ScoringEndpointsIT` 14, `ScoringNoActiveRulesetIT` 1, `ScoringRulesetEndpointsIT`, `FlywayMigrationIT`, `CrossModuleE2EIT`) | — | **verde** | `CaseRagServiceIT` 7/7 · `ScoringEndpointsIT` +2 |
| Backend integración — suite completa `./mvnw clean verify` (tras I2) | *(no ejecutada en V2-0)* | _(en curso — se anota el resultado al terminar)_ | — |
| Aislamiento de tenant (por recurso nuevo) | n/a | **checklist** (I1) + **3 gates** (I3) + **RAG** (I2): `CaseRagServiceIT.scoringResultOfAnotherTenantNeverLeaksIntoTheIndicator`, `ScoringEndpointsIT.ragFromAnotherTenantCaseIsNotFound` | — |

---

## Incidencias

| ID | Fecha | Descripción | Estado |
|---|---|---|---|
| **I-01** | 2026-08-31 | 4 archivos locales sin commitear en `frontend/src/app/features/clients/client-form/` (trabajo "Sprint 40.x"). **Resuelto en V2-0:** commit independiente `5c4b223` en `main`, sin modificar. `feat/v2-migration` parte de ahí. | ✅ CERRADA |

---

## Decisiones pendientes

| ID | Ámbito | Pregunta | Bloqueante para |
|---|---|---|---|
| — | — | **Ninguna decisión de alcance pendiente.** Todas resueltas en `BRIKKA_V2_MIGRATION_SCOPE.md §10`. | — |

Decisiones **de diseño interno** (se resuelven dentro de cada sprint, sin bloquear el alcance):
- I1: ¿estado de completitud derivado en el servicio o columna `document_requests.fulfilled_at` / `fulfilled_by_version_id`?
- I3: nombre y nivel del permiso de excepción (`CASE_TRANSITION_OVERRIDE` u otro del catálogo RBAC).
- I4: columnas nuevas vs `metadata` tipado para el desglose de interés; `ico_guarantee` en `cases` vs `properties`; bonificaciones en tabla propia vs `metadata`.
- I5: dossier + ZIP como una única descarga combinada vs dossier HTML con enlace al ZIP.

---

## Registro de avance (cronológico)

| Fecha | Bloque | Cambio | Tests |
|---|---|---|---|
| 2026-08-31 | — | Fases 0–7. Creados `BRIKKA_V2_FUNCTIONAL_GAP.md`, `BRIKKA_V2_BUSINESS_RULES_GAP.md`, `BRIKKA_V2_MIGRATION_SCOPE.md`. Decisiones del propietario (§10). Creado este PROGRESS. **Sin cambios de código.** | n/a |
| 2026-08-31 | V2-0 | Commit Sprint 40.x (`5c4b223`, `main`). Rama `feat/v2-migration`. Baseline de tests medido. Docs de planificación commiteados en la rama. | FE 485 ✅ · BE unit 106 ✅ · BE IT muestra 26 ✅ |
| 2026-08-31 | V2-1 (I1) | Checklist documental: `V27` + 7 clases backend nuevas + 8 modificadas + 3 ficheros frontend + selector de titular. Cierre por `APPROVED` (§10.3), auto-gen en `DOCUMENTATION`, AI-ready. | `CaseChecklistServiceIT` 6/6 · `DocumentEndpointsIT` 16/16 · `FlywayMigrationIT` ✅ · barrido regresión ✅ · FE 488/488 ✅ |
| 2026-08-31 | V2-2 (I3) | 3 gates de transición (`CaseTransitionPreconditions` + `CaseService.changeStatus`) + excepción autorizada (`CASE_TRANSITION_OVERRIDE`, `V28`) + FE (casilla forzar + traducción de errores). | `CaseTransitionPreconditionsIT` 15/15 · `CrmCaseEndpointsIT` 23/23 · `CaseServiceIT` 17/17 · RBAC counters ✅ · FE 492/492 ✅ · `./mvnw verify` completo → ver abajo |
| 2026-08-31 | V2-3 (I2) | Scoring de fábrica (`V29` — ruleset `ACTIVE` `default-operation-v1` + 4 reglas + categorías `GREEN/AMBER/RED` en jsonb, motor existente) + indicador RAG del expediente (`scoring/CaseRagService` + `GET /scoring/rag`, `SCORING_READ`, peor de 3 ejes tenant-scoped) + FE (`features/scoring/*` + sección "Indicador RAG" en `case-detail` + `status-tone`/labels). `case-list` RAG → FUTURO `§6.5`. | `CaseRagServiceIT` 7/7 · `ScoringEndpointsIT` 16/16 (+2) · `ScoringNoActiveRulesetIT` adaptado ✅ · `FlywayMigrationIT` 28→29 ✅ · FE 499/499 ✅ · `ng lint` ✅ · `./mvnw clean verify` completo → ver abajo |

---

## Declaración de cierre

> Pendiente. Se emitirá **MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA** cuando la barra global esté al
> 100 % (I1–I5 con DoD cumplida y batería completa en verde) y este documento y `CHANGELOG.md` /
> `docs/12_DECISION_LOG.md` estén actualizados. A partir de esa declaración **no se añaden más
> funcionalidades de migración**; todo lo nuevo va a `BRIKKA_V2_MIGRATION_SCOPE.md §6 FUTURO`.
