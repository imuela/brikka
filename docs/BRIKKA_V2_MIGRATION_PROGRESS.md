# BRIKKA V2 — SEGUIMIENTO DE LA MIGRACIÓN (LEGACY → BRIKKA V2)

> Documento **vivo**. Se actualiza al cerrar cada tarea/bloque. El porcentaje refleja **trabajo real
> completado y validado** (backend + frontend + tests en verde), no estimaciones optimistas.
>
> Alcance: `BRIKKA_V2_MIGRATION_SCOPE.md` (I1–I5; I6 → FUTURO). La migración **termina** cuando I1–I5
> cumplen la condición de §7 de ese documento y se declara **MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA**.

---

## Barra de progreso global

```
V2  [████████████████████████████]  100 %   (5 / 5 bloques · I1 ✅ · I2 ✅ · I3 ✅ · I4 ✅ · I5 ✅)   —   MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA
```

| Bloque | Peso | Estado | Backend | Frontend | Tests BE | Tests FE | % bloque |
|---|---:|---|---|---|---|---|---:|
| **I1** · Checklist documental | 25 % | ✅ Completado | ✅ | ✅ | ✅ | ✅ | 100 % |
| **I2** · Scoring de fábrica + RAG | 20 % | ✅ Completado | ✅ | ✅ | ✅ | ✅ | 100 % |
| **I3** · Precondiciones de transición | 15 % | ✅ Completado | ✅ | ✅ | ✅ | ✅ | 100 % |
| **I4** · Simulación enriquecida | 25 % | ✅ Completado | ✅ | ✅ | ✅ | ✅ | 100 % |
| **I5** · Dossier + ZIP + narrativa | 15 % | ✅ Completado | ✅ | ✅ | ✅ | ✅ | 100 % |
| **TOTAL** | 100 % | | | | | | **100 %** |

Leyenda: ⬜ pendiente · 🟨 en curso · ✅ completado y validado.

---

## Bloque / sprint actual

**Ninguno.** I1–I5 cerrados. La migración Legacy → BRIKKA V2 actualmente aprobada está **COMPLETADA**
(ver "Declaración de cierre"). A partir de aquí, cualquier funcionalidad Legacy adicional o mejora
va a `BRIKKA_V2_MIGRATION_SCOPE.md §6 FUTURO` y **no se implementa**.

### V2-5 · I5 · Dossier + ZIP documental + narrativa determinista — ✅ CERRADO (2026-08-31)

Commit del sprint: pendiente de `git commit` (ver "Registro de avance"). **Sin migración** (no hay
cambio de esquema). Sin permiso nuevo.

**1. ZIP de documentación — `document/CaseDocumentsArchiveService` + `GET /api/v1/cases/{caseId}/documents/archive`**

- **Streaming real:** `StorageClient.openStream(key)` nuevo (implementado en `S3StorageClient` con
  `s3Client.getObject`, devuelve el stream de la respuesta HTTP — no un mecanismo de almacenamiento
  paralelo). El controlador devuelve `StreamingResponseBody`; el servicio abre **un stream de
  documento a la vez** y lo copia (`InputStream.transferTo`) al `ZipOutputStream` que escribe
  directo a la respuesta. Nunca se acumula el ZIP en memoria. Verificado en
  `CaseDocumentsArchiveServiceTest` (max. 1 stream abierto simultáneamente con 25 documentos).
- **Contenido:** la **versión actual** (`documents.current_version_id`) de cada `documents` del
  caso — exactamente la que devuelve `GET /api/v1/documents/{id}/download`. **Sin filtro de
  aprobación/publicación** (el endpoint de un solo documento tampoco lo tiene). Documentos sin
  versión subida → omitidos. Dossier/contrato generados (HTML) → incluidos (son documentos del
  caso). **Semántica de versionado inmutable intacta** — solo lectura.
- **Estructura interna (derivada de metadatos actuales, NO de las carpetas Legacy `01–06`):**
  `<tipo de documento>/<titular | "expediente">/<documentId>-<nombre original>`. Cada segmento pasa
  por `SafeFilenames.sanitize` (hecho `public`) + guarda contra `.` / `..` / vacío. El prefijo
  `documentId` hace único cada nombre. **Path traversal imposible** (test con metadatos hostiles:
  `../../etc/passwd`, nombre de titular con comillas y `../..`).
- **Seguridad:** `caseAccessService.requireCaseAccess(auth, "DOCUMENT_DOWNLOAD", caseId)` **antes**
  de tocar ningún documento → tenant + rol + asignación de BROKER; caso de otro tenant → **404**
  (enmascarado, no revela la existencia de documentos); manipular `caseId` a otro caso aplica el
  control de ese caso. Guarda defensiva por documento (`companyId == tenant`). Permiso reutilizado
  `DOCUMENT_DOWNLOAD` (SUPERADMIN/MANAGER/BROKER) — **sin permiso nuevo**. Auditoría
  `CASE_DOCUMENTS_ARCHIVE_DOWNLOADED`.
- **Errores:** el control de acceso y el `count == 0` ocurren **antes** de arrancar el cuerpo →
  403 / 404 / **400 `CASE_HAS_NO_DOCUMENTS`** son JSON limpio `{code,message,requestId}`. Un fallo
  de almacenamiento a mitad del stream aborta la descarga (registrado en el servicio) — patrón
  estándar de `StreamingResponseBody`, sin sistema de errores nuevo.
- **Respuesta:** `application/zip`, `Content-Disposition: attachment; filename="expediente-<ref
  saneada>-documentos.zip"`, nombre determinista.

**2. Narrativa determinista — `dossier/CaseNarrativeService`** (elevando el dossier existente, **sin
dossier paralelo**):

- `narrate(Case) → CaseNarrative` = lista ordenada de 8 `NarrativeSection` (`situation`, `holders`,
  `property`, `financing`, `scoring`, `viability`, `documentation`, `fees`), cada una con frases en
  español listas para renderizar. **Sin IA, sin `AiProvider`, sin Ollama, sin dependencia externa,
  sin lectura de reloj en el texto** → `narrate(x)` siempre devuelve lo mismo para los mismos datos
  (test dedicado + test HTTP de determinismo).
- **Fuentes (solo datos ya almacenados):** `Case` (referencia, estado legible, tipo, importe,
  fecha) · `case_clients` HOLDER/CO_HOLDER + `Client` + `ClientFinancialProfile` (ingresos,
  empleo/empleador/antigüedad, ahorro, deudas) · `Property` (tipo, valoración, precio) + **LTV
  aproximado** (misma fórmula `amount / MIN(valoración, precio)` que `ScoreInputSnapshotFactory`) ·
  `FinancingRequest` + `Simulation` (tipo de interés, tipo final, cuota; para MIXED, tramo fijo +
  cuota del tramo variable vía `SimulationService.computationOf`) · último `scoring_result`
  (categoría + puntuación, **filtrado por `companyId`**) + `CaseRagService.evaluate` (indicador RAG
  + ejes, reutiliza I2) · `case_financial_analysis_results` (última por titular, **filtrada por
  `companyId`**, categoría + DTI) · `CaseChecklistService.checklist` (obligatorios aprobados / total)
  · `CaseFee` (importe, tipo, estado).
- Dato ausente → frase explícita ("Sin inmueble registrado", "Scoring de la operación no
  calculado", "sin evaluar", "Sin análisis de viabilidad ejecutado"…), **nunca inventado**. **Sin
  recomendaciones bancarias ni conclusiones financieras nuevas.** Los datos de otro tenant nunca
  aparecen (test dedicado).
- **`ViabilityDossierService` elevado:** el HTML del dossier deja de ser un volcado de campos — su
  cuerpo son las secciones de la narrativa (`<h2>{título}</h2>` + `<p>{frase}</p>`). Se conservan
  el banner de aviso, el pie con timestamp de snapshot (lo único no determinista, distingue cada
  versión), el mismo endpoint `POST/GET /dossier`, el mismo `Document` versionado tipo
  `VIABILITY_DOSSIER`, el mismo `NO_CLIENTS_ON_CASE`. El servicio pasó de 389 a ~130 líneas.
- **`GET /api/v1/cases/{caseId}/dossier/narrative`** (nuevo, solo lectura, `DOCUMENT_READ`, sin
  persistencia): la misma narrativa como JSON estructurado para `case-detail`.

**3. Frontend** (solo `case-detail`, sin rediseño):
- Sección "Dossier de viabilidad": panel **"Narrativa del expediente"** (secciones + párrafos,
  cargado en el init) + botón **"Descargar toda la documentación (ZIP)"**
  (`*appHasPermission="'DOCUMENT_DOWNLOAD'"`). Loading / error / "sin documentos" gestionados.
- La descarga usa `ApiClient.getBlob` (nuevo) → el interceptor de auth adjunta el bearer, a
  diferencia de `window.open` sobre una URL presignada; blob → `<a download>` con el nombre del
  `Content-Disposition`.
- `case-archive.service.ts` (nuevo), `viability-dossier.{model,service}` +`getNarrative` /
  `CaseNarrative`, `error-messages.ts` +`CASE_HAS_NO_DOCUMENTS`.

**Tests (backend, todos verdes):**
- `CaseDocumentsArchiveServiceTest` (nuevo, unit) — **2/2**: un stream de documento a la vez + todos
  cerrados (25 docs); nombres de entrada saneados con metadatos hostiles (sin `..`, sin ruta
  absoluta).
- `CaseNarrativeServiceIT` (nuevo, Postgres) — **7/7**: caso completo (8 secciones pobladas), caso
  disperso ("no disponible" por sección), determinismo, **datos de otro tenant nunca aparecen**,
  múltiples titulares nombrados, la simulación descrita es la más reciente, caso sin titulares no
  rompe la narrativa.
- `CaseNarrativeEndpointsIT` (nuevo) — **5/5**: 8 secciones estructuradas, determinismo por HTTP,
  otro tenant → 404, BROKER sin asignación → 403, sin autenticar → 401.
- `DocumentEndpointsIT` (+5, **21/21**) — ZIP válido con las versiones actuales + bytes correctos +
  auditoría; nunca incluye documentos de otro caso; sin documentos → 400 `CASE_HAS_NO_DOCUMENTS`;
  BROKER sin asignación → 403; otro tenant → 404.
- Regresión: `ViabilityDossierEndpointsIT` 8/8 (el dossier elevado no rompe nada),
  `EngagementContractEndpointsIT`, `DocumentServiceIT`, `CrossModuleE2EIT`, scoring/viabilidad/
  simulación — verdes.

**Tests (frontend, todos verdes):** `case-archive.service.spec` · `viability-dossier.service.spec`
(`getNarrative`) · `case-detail.component.spec` (+3: narrativa renderizada, descarga ZIP por blob
autenticado, mensaje específico "sin documentos"). `ng test` **507/507** · `ng lint` verde.

**FUTURO (detectado, no implementado):** ninguna funcionalidad Legacy nueva. La IA para la
narrativa (resumen/explicación verbalizada), OCR, extracción y clasificación documental siguen en
`SCOPE §6.4 FUTURO` — I5 entrega la narrativa **determinista**, la costura para un `AiProvider`
posterior es el propio `CaseNarrativeService`.

### V2-4 · I4 · Simulación hipotecaria enriquecida — ✅ CERRADO (2026-08-31)

Commit del sprint: pendiente de `git commit` (ver "Registro de avance"). Implementado sobre el motor
de cuota francés existente (`MortgagePaymentCalculator`) — **sin segundo motor de cálculo**, sin
recuperar bugs Legacy (`monthly_payment_phase2`, `total_interest`, `recommended`, bonificaciones sin
aplicar).

**1. Modelo de datos — `V30__simulation_interest_type_and_bonifications.sql`** (aditivo, no
destructivo, sin tabla ni permiso nuevos). 9 columnas nuevas en `simulations`:

| columna | tipo | notas |
|---|---|---|
| `interest_type` | `varchar(20) NOT NULL DEFAULT 'FIXED'` + CHECK `IN (FIXED,VARIABLE,MIXED)` | filas históricas → `FIXED` |
| `base_interest_rate` | `numeric(7,4)` → `NOT NULL` tras backfill | tipo antes de bonificaciones |
| `final_interest_rate` | `numeric(7,4)` → `NOT NULL` tras backfill | `= interest_rate` (tipo efectivo) |
| `euribor_rate`, `spread_rate` | `numeric(7,4)` NULL | solo VARIABLE / MIXED |
| `fixed_period_months` | `integer` NULL + CHECK `> 0` | solo MIXED |
| `fixed_period_rate` | `numeric(7,4)` NULL | solo MIXED (tipo del tramo fijo, pre-bonif.) |
| `ico_guarantee` | `boolean NOT NULL DEFAULT false` | dato, no motor de elegibilidad |
| `bonifications` | `jsonb NOT NULL DEFAULT '[]'` | array `{code,label,rate,active}` — datos, no seis códigos hardcoded en Java |

- **`interest_rate`** (columna existente) se conserva como **tipo efectivo** que leen aguas abajo
  `FinancialAnalysisService` y `ViabilityDossierService` → siempre `= final_interest_rate` (para
  MIXED, el final del **tramo fijo**). **`estimated_payment`** (existente) pasa a **calcularse en el
  servidor** (antes lo tecleaba el bróker).
- **Backfill:** `UPDATE simulations SET base_interest_rate = interest_rate, final_interest_rate =
  interest_rate WHERE base_interest_rate IS NULL` → las filas antiguas quedan como `FIXED` sin
  bonificaciones, `estimated_payment` intacto. Ninguna conversión silenciosa; ninguna columna se
  elimina.
- **`MortgagePaymentCalculator` movido** de `financialanalysis` a `financing` (paquete correcto:
  `financialanalysis` ya dependía de `financing`; así se evita un ciclo de paquetes). Es la misma
  clase; consumidores (`FinancialAnalysisService`) actualizan el `import`.

**2. Cálculo — `financing/SimulationInterestCalculator`** (puro, estático, determinista; usa
`MortgagePaymentCalculator`):

| tipo | tipo base | tipo final | cuota |
|---|---|---|---|
| **FIXED** | `fixedRate` | `max(0, base − Σ bonif. activas)` | francés a `final` sobre el plazo total |
| **VARIABLE** | `euribor + spread` | `max(0, base − Σ)` | francés a `final` sobre el plazo total |
| **MIXED** | `fixedPeriodRate` (tramo fijo) | `max(0, base − Σ)` | **tramo fijo:** francés a `final` sobre plazo total · **tramo variable:** re-amortiza el saldo pendiente (`MortgagePaymentCalculator.computeOutstandingBalance`, nueva — misma familia de fórmula) a `max(0, euribor+spread − Σ)` sobre los meses restantes |

- **Bonificaciones (R19, aplicadas de verdad):** `Σ` = suma de `rate` de las entradas con
  `active = true`; se resta al tipo base; *floor* 0. La misma `Σ` se aplica a **ambos tramos** de un
  MIXED (una bonificación —nómina, seguro— es condición de la hipoteca completa).
- **Redondeo (documentado):** suma de bonificaciones exacta (`BigDecimal`); cada tipo (base, final)
  a escala 4 `HALF_UP` una vez; toda cuota y el saldo pendiente a escala 2 `HALF_UP` (los redondea
  `MortgagePaymentCalculator`, comportamiento ya existente). Nunca `double`/`float`.
- **Alcance MIXED (documentado):** el tramo variable se proyecta con el euríbor introducido; no se
  modela una senda futura del euríbor. `interest_rate` (lo que consumen aguas abajo) = tipo del
  tramo fijo, el que rige al inicio del préstamo. La fase variable (saldo + cuota) **no se persiste**
  — se re-deriva al leer desde los datos guardados (determinista, reproducible).

**3. Validación — `financing/SimulationService`** (validación de dominio → `ValidationException` →
400 `{code,message,requestId}`; el código base **no usa Bean Validation**, decisión Sprint 40):

| código | caso |
|---|---|
| `INVALID_SIMULATION_INTEREST_TYPE` | tipo no ∈ {FIXED,VARIABLE,MIXED} |
| `INVALID_SIMULATION_AMOUNT` / `INVALID_SIMULATION_TERM` | principal ≤ 0 · plazo < 1 |
| `SIMULATION_INTEREST_MODEL_MISMATCH` | campo requerido ausente **o** campo no aplicable presente (FIXED con euríbor, VARIABLE sin diferencial, MIXED sin tramo fijo…) |
| `INVALID_SIMULATION_FIXED_PERIOD` | MIXED con `fixedPeriodMonths` ∉ [1, plazo − 1] |
| `NEGATIVE_SIMULATION_VALUE` | tipo fijo / diferencial / tramo fijo negativo (el **euríbor sí puede ser negativo**) |
| `INVALID_SIMULATION_BONIFICATION` | código en blanco / duplicado, `rate` nulo o negativo, código desconocido sin descripción |

`final ≤ base` siempre por construcción (las bonificaciones solo reducen). Catálogo
`SimulationBonificationCatalog` = etiquetas de los códigos conocidos (PAYROLL, HOME_INSURANCE, …) —
**solo presentación**, el cálculo no lo consulta; se admiten códigos no listados con descripción.

**4. API — `POST` / `GET /api/v1/cases/{caseId}/simulations`** (endpoints existentes extendidos, sin
API paralela). Permisos `SIMULATION_CREATE` / `SIMULATION_READ` sin cambios; tenant vía
`CaseAccessService` (caso de otro tenant → 404); contrato de error intacto.
`CreateSimulationApiRequest`: `interestType, principal, termMonths, fixedRate?, euriborRate?,
spreadRate?, fixedPeriodMonths?, fixedPeriodRate?, bonifications[], icoGuarantee?, metadata`.
`SimulationResponse`: + `interestType, baseInterestRate, finalInterestRate, euriborRate, spreadRate,
fixedPeriodMonths, fixedPeriodRate, icoGuarantee, bonifications[], variablePhase` (`{baseInterestRate,
finalInterestRate, outstandingBalanceAtSwitch, monthlyPayment}`, solo MIXED).

**5. Frontend** (solo pantallas de simulación):
- `create-simulation-dialog`: selector de tipo → campos adaptados (FIXED: tipo fijo · VARIABLE:
  euríbor + diferencial · MIXED: meses/tipo del tramo fijo + euríbor + diferencial); lista de
  bonificaciones (catálogo + "Otra", descripción, reducción, activa); casilla aval ICO;
  **previsualización** de tipo base / suma de bonificaciones / tipo final (aritmética trivial en
  cliente — **no** se duplica la fórmula de cuota). Tras crear, muestra un **resumen** con tipo base,
  tipo final y **cuota** (del backend), y para MIXED la cuota del tramo variable.
- `financing.model.ts`: `Simulation` / `CreateSimulationRequest` enriquecidos + `SimulationBonification`
  + `SimulationVariablePhase` + `INTEREST_TYPES`.
- `case-detail`: columna **"Tipo"** en la tabla de simulaciones (badge + "· aval ICO"); "Interés" →
  "Interés final".
- `status-labels.ts`: `INTEREST_TYPE_LABELS`. `error-messages.ts`: +7 códigos de simulación.

**Tests (backend, todos verdes):**
- `SimulationInterestCalculatorTest` (nuevo, unit) — **7/7**: FIXED sin/con bonif. + *floor* 0 +
  cuota; VARIABLE euríbor+diferencial (+ euríbor negativo) + bonif. + cuota; MIXED dos tramos con
  bonif. en ambos + saldo pendiente + cuota variable; suma de bonif. activas.
- `SimulationServiceValidationTest` (nuevo, unit) — **12/12**: los 7 códigos de error, mezcla
  campos/tipo, tramo fijo ≥ plazo, diferencial negativo, bonif. duplicada / negativa / en blanco /
  desconocida sin descripción.
- `MortgagePaymentCalculatorTest` (+5) — `computeOutstandingBalance`: k=0 → principal, k≥plazo → 0,
  tipo 0 → lineal, monotonía + escala, re-amortización reproduce la cuota original.
- `FinancingEndpointsIT` (reescrito + ampliado, **11/11**) — FIXED aplica bonif. y devuelve la cuota
  calculada; VARIABLE euríbor+diferencial−bonif.; MIXED devuelve ambos tramos; ICO true/false
  persistido; modelo inválido → 400 estructurado (`SIMULATION_INTEREST_MODEL_MISMATCH`,
  `INVALID_SIMULATION_FIXED_PERIOD`); **caso de otro tenant → 404**; sin asignación → 403; CLIENT → 403.
- `FlywayMigrationIT` — 29→30; +asserts de las 9 columnas de `simulations`, `interest_type` NOT NULL,
  `ico_guarantee` default false.
- Regresión: `FinancialAnalysisEndpointsIT` 10/10, `ViabilityDossierEndpointsIT` 8/8,
  `EngagementContractEndpointsIT` 7/7, `CrossModuleE2EIT` 3/3 — verdes tras mover
  `MortgagePaymentCalculator` y añadir el `insert` de bajo nivel (compat.).

**Tests (frontend, todos verdes):** `create-simulation-dialog.component.spec` (reescrito) — FIXED con
bonif. + body exacto + resumen; VARIABLE adapta campos + body; MIXED muestra cuota del tramo variable;
ICO; previsualización de tipos; no envía inválido; error de backend. `financing.service.spec`
actualizado. `ng test` **501/501** · `ng lint` verde.

**FUTURO (detectado, no implementado):** ninguno nuevo en I4.

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

### I4 · Simulación enriquecida  *(P1 — Sprint V2-4 · ✅ COMPLETADO 2026-08-31)*

**Definition of Done:**
- [x] Migración `V30__simulation_interest_type_and_bonifications.sql` (aditiva, no destructiva):
      `interest_type` (`FIXED`/`VARIABLE`/`MIXED`, CHECK), `base_interest_rate` /
      `final_interest_rate` `numeric(7,4)`, `euribor_rate`, `spread_rate`, `fixed_period_months`,
      `fixed_period_rate`, `ico_guarantee boolean DEFAULT false`, `bonifications jsonb` (array
      tipado — datos, no seis códigos en Java). Backfill de filas históricas (FIXED, base = final =
      `interest_rate`). **Antes V29; V29 la ocupó I2.**
- [x] `final_interest_rate = max(0, base − Σ bonificaciones activas)` **en el backend**
      (`SimulationInterestCalculator`), aplicado de verdad (Legacy no lo hacía).
- [x] `estimated_payment` recalculado con `MortgagePaymentCalculator` (movido a `financing`) a
      partir de `final_interest_rate`. MIXED: cuota del tramo fijo + cuota del tramo variable
      re-amortizando el saldo pendiente (`computeOutstandingBalance`, nueva; misma familia de
      fórmula).
- [x] Validación `MIXED`: `fixedPeriodMonths` ∈ [1, plazo − 1] (`INVALID_SIMULATION_FIXED_PERIOD`).
- [x] Modelo de interés explícito + validación de dominio (`SimulationService`, 7 códigos de error;
      FIXED no admite euríbor, VARIABLE exige euríbor+diferencial, etc.). ICO como dato en
      `simulations` (no motor de elegibilidad).
- [x] `create-simulation-dialog` (Angular): tipo de interés → campos adaptados, bonificaciones, ICO;
      muestra tipo base / suma de bonificaciones / tipo final y, al crear, la **cuota** (del backend).
- [x] Tests BE: `SimulationInterestCalculatorTest` 7/7 · `SimulationServiceValidationTest` 12/12 ·
      `MortgagePaymentCalculatorTest` +5 (saldo pendiente) · `FinancingEndpointsIT` 11/11 (FIXED /
      VARIABLE / MIXED / ICO / modelo inválido / **tenant → 404**) · `FlywayMigrationIT` 29→30.
- [x] Tests FE: `create-simulation-dialog.component.spec` reescrito · `financing.service.spec`
      actualizado.
- [x] Regresión completa en verde (incl. `FinancialAnalysisEndpointsIT`, `ViabilityDossierEndpointsIT`).
- [x] `docs/` (`SCOPE §1 I4` + tabla de migración) y este PROGRESS actualizados.

**Criterio de aceptación:** una simulación `VARIABLE` con euríbor + diferencial + bonificaciones
activas muestra tipo base, tipo final y cuota coherentes.  ✅ verificado en
`variableSimulationUsesEuriborPlusSpreadMinusBonifications` + `SimulationInterestCalculatorTest`.

### I5 · Dossier + ZIP documental + narrativa determinista  *(P1 — Sprint V2-5 · ✅ COMPLETADO 2026-08-31)*

**Definition of Done:**
- [x] `document/CaseDocumentsArchiveService` + `GET /api/v1/cases/{caseId}/documents/archive`
      (`StreamingResponseBody`): ZIP **en streaming** (un stream de documento a la vez,
      `StorageClient.openStream` nuevo, sin fichero temporal, sin acumular en memoria) con la
      **versión actual** de cada `Document` del caso, ruta `<tipo>/<titular|"expediente">/<docId>-<nombre>`
      (metadatos actuales, **no** carpetas Legacy `01–06`), nombres saneados (sin path traversal),
      `DOCUMENT_DOWNLOAD` + tenant/caso/asignación + guarda por documento. Sin migración.
- [x] Versionado: se usa `documents.current_version_id` (igual que la descarga de un documento);
      sin política nueva; semántica inmutable intacta. Documentado.
- [x] `ViabilityDossierService` **elevado** (no un segundo dossier): su HTML pasa a ser la narrativa
      determinista sección a sección; mismo endpoint, mismo `Document` versionado, mismo snapshot.
- [x] `dossier/CaseNarrativeService` (Java, reglas, **sin IA / sin `AiProvider` / sin Ollama**):
      resumen estructurado de 8 secciones (situación, titulares, inmueble + LTV, financiación,
      scoring + RAG, viabilidad/DTI, documentación, honorarios) solo con datos almacenados; dato
      ausente → frase explícita; determinista; sin recomendaciones ni conclusiones nuevas. Costura
      AI-ready = el propio servicio. `GET /dossier/narrative` (solo lectura, `DOCUMENT_READ`).
- [x] `case-detail`: botón "Descargar toda la documentación (ZIP)" (blob autenticado) + panel de
      narrativa. Sin rediseño.
- [x] Tests BE: `CaseDocumentsArchiveServiceTest` 2/2 (streaming + nombres seguros) ·
      `CaseNarrativeServiceIT` 7/7 · `CaseNarrativeEndpointsIT` 5/5 · `DocumentEndpointsIT` +5
      (ZIP válido · nunca de otro caso · sin docs → 400 · 403 · 404) · regresión dossier/contrato/e2e.
- [x] Tests FE: `case-archive.service.spec` · `viability-dossier.service.spec` ·
      `case-detail.component.spec` (+3).
- [x] Batería completa en verde.
- [x] `docs/` (`SCOPE §1 I5` + §7) y este PROGRESS actualizados.

**Criterio de aceptación:** el equipo del caso descarga un ZIP con todos los documentos del
expediente y el dossier incluye la narrativa determinista.  ✅ verificado en
`caseDocumentsArchiveStreamsAValidZipWithTheCurrentVersions` + `CaseNarrativeServiceIT` +
`ViabilityDossierEndpointsIT`.

---

## Tests — estado

| Ámbito | Baseline (V2-0) | Actual (tras I5) | Nuevos en V2 |
|---|---|---|---|
| Frontend (`ng lint` + `npm test`) | lint ✅ · **485/485** | lint ✅ · **507/507** | +22 |
| Backend unit (Surefire, sin Docker) | **106/106** ✅ | **134/134** ✅ | +28 (I4: 24; I5: `CaseDocumentsArchiveServiceTest` 2; I3: 2) |
| Backend IT — barrido I1 (document, casemgmt, contract, dossier, financialanalysis, ai, e2e, portal, notification) | — | **verde** (189 tests) | I1 |
| Backend IT — barrido I3 (`CaseTransitionPreconditionsIT` 15, `CrmCaseEndpointsIT` 23, `CaseServiceIT` 17, `CaseChecklistServiceIT` 6, `FlywayMigrationIT`, `RbacSeedIT` 30, `IdentityEndpointsIT`) | — | **verde** | `CaseTransitionPreconditionsIT` 15/15 · `CrmCaseEndpointsIT` +2 |
| Backend IT — barrido I2 (`CaseRagServiceIT` 7, `ScoringEndpointsIT` 14, `ScoringNoActiveRulesetIT` 1, `ScoringRulesetEndpointsIT`, `FlywayMigrationIT`, `CrossModuleE2EIT`) | — | **verde** | `CaseRagServiceIT` 7/7 · `ScoringEndpointsIT` +2 |
| Backend IT — barrido I4 (`FinancingEndpointsIT` 11, `FinancialAnalysisEndpointsIT` 10, `ViabilityDossierEndpointsIT` 8, `EngagementContractEndpointsIT` 7, `CrossModuleE2EIT` 3, `FlywayMigrationIT`) | — | **verde** | `FinancingEndpointsIT` +expandido · `SimulationInterestCalculatorTest` 7 · `SimulationServiceValidationTest` 12 · `MortgagePaymentCalculatorTest` +5 |
| Backend IT — barrido I5 (`DocumentEndpointsIT` 21, `CaseNarrativeServiceIT` 7, `CaseNarrativeEndpointsIT` 5, `ViabilityDossierEndpointsIT` 8, `EngagementContractEndpointsIT` 7, `DocumentServiceIT`, `CrossModuleE2EIT` 3) | — | **verde** | `DocumentEndpointsIT` +5 · `CaseNarrativeServiceIT` 7 · `CaseNarrativeEndpointsIT` 5 · `CaseDocumentsArchiveServiceTest` 2 |
| Backend integración — suite completa `./mvnw clean verify` (tras I2) | *(no ejecutada en V2-0)* | **BUILD SUCCESS** — Surefire **108/108**, Failsafe **444/444** (72 clases IT), 0 fallos / 0 errores | — |
| Backend integración — suite completa `./mvnw clean verify` (tras I4) | — | **BUILD SUCCESS** — Surefire **132/132**, Failsafe **449/449**, 0 fallos / 0 errores; `spotless:check` ✅ | — |
| Backend integración — suite completa `./mvnw clean verify` (tras I5) | — | **BUILD SUCCESS** — Surefire **134/134**, Failsafe **466/466**, 0 fallos / 0 errores; `spotless:check` ✅ | — |
| Aislamiento de tenant (por recurso nuevo) | n/a | **checklist** (I1) + **3 gates** (I3) + **RAG** (I2) + **simulación** (I4, `simulationFromAnotherTenantCaseIsNotFound`) + **ZIP y narrativa** (I5, `caseDocumentsArchiveFromAnotherTenantIsNotFound` / `caseDocumentsArchiveNeverIncludesAnotherCasesDocuments` / `anotherTenantScoringAndViabilityNeverAppear` / `narrativeFromAnotherTenantIsNotFound`) | — |

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
| 2026-08-31 | V2-3 (I2) | Scoring de fábrica (`V29` — ruleset `ACTIVE` `default-operation-v1` + 4 reglas + categorías `GREEN/AMBER/RED` en jsonb, motor existente) + indicador RAG del expediente (`scoring/CaseRagService` + `GET /scoring/rag`, `SCORING_READ`, peor de 3 ejes tenant-scoped) + FE (`features/scoring/*` + sección "Indicador RAG" en `case-detail` + `status-tone`/labels). `case-list` RAG → FUTURO `§6.5`. | `CaseRagServiceIT` 7/7 · `ScoringEndpointsIT` 16/16 (+2) · `ScoringNoActiveRulesetIT` adaptado ✅ · `FlywayMigrationIT` 28→29 ✅ · FE 499/499 ✅ · `ng lint` ✅ · `./mvnw clean verify` → Surefire 108/108 · Failsafe 444/444 · BUILD SUCCESS |
| 2026-08-31 | V2-4 (I4) | Simulación enriquecida: `V30` (9 columnas en `simulations`, aditiva) + `financing/SimulationInterestCalculator` (FIXED/VARIABLE/MIXED, bonificaciones aplicadas de verdad, floor 0, MIXED dos tramos) + `financing/SimulationService` (validación de dominio, 7 códigos) + `MortgagePaymentCalculator` movido a `financing` + `computeOutstandingBalance` · `POST/GET /simulations` extendidos (sin API paralela) · FE `create-simulation-dialog` adaptado + `case-detail` columna "Tipo". Sin recuperar bugs Legacy. | `SimulationInterestCalculatorTest` 7/7 · `SimulationServiceValidationTest` 12/12 · `MortgagePaymentCalculatorTest` +5 · `FinancingEndpointsIT` 11/11 · `FlywayMigrationIT` 29→30 ✅ · regresión (financialanalysis/dossier/contract/e2e) ✅ · FE 501/501 ✅ · `ng lint` ✅ · `./mvnw clean verify` → Surefire 132/132 · Failsafe 449/449 · BUILD SUCCESS |
| 2026-08-31 | V2-5 (I5) | Dossier + ZIP + narrativa: `document/CaseDocumentsArchiveService` + `GET /cases/{id}/documents/archive` (`StreamingResponseBody`, `StorageClient.openStream` nuevo, un stream a la vez, versión actual de cada documento, ruta `<tipo>/<titular>/<docId>-<nombre>` saneada, `DOCUMENT_DOWNLOAD`) · `dossier/CaseNarrativeService` determinista (8 secciones, sin IA) elevando `ViabilityDossierService` (389→~130 líneas) + `GET /dossier/narrative` · FE `case-detail` (botón ZIP blob autenticado + panel de narrativa) + `ApiClient.getBlob`. **Sin migración, sin permiso nuevo.** | `CaseDocumentsArchiveServiceTest` 2/2 · `CaseNarrativeServiceIT` 7/7 · `CaseNarrativeEndpointsIT` 5/5 · `DocumentEndpointsIT` 21/21 (+5) · regresión (dossier/contract/e2e/document) ✅ · FE 507/507 ✅ · `ng lint` ✅ · `./mvnw clean verify` → Surefire 134/134 · Failsafe 466/466 · BUILD SUCCESS |

---

## Declaración de cierre

> **MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA** — 2026-08-31.
>
> Los cinco bloques del alcance aprobado (`BRIKKA_V2_MIGRATION_SCOPE.md §1`) están cerrados con su
> Definition of Done cumplida y batería completa en verde:
> - **I1** · Checklist documental del expediente (V27) — commit `f07c919`.
> - **I3** · Precondiciones de transición + excepción autorizada (V28) — commit `3e00857`.
> - **I2** · Scoring de fábrica + indicador RAG del expediente (V29) — commit `33f4d18`.
> - **I4** · Simulación hipotecaria enriquecida (V30) — commit `cd6218c`.
> - **I5** · ZIP de documentación en streaming + narrativa determinista del dossier — commit V2-5.
>
> Se cumple la condición de cierre de `SCOPE §7`: I1–I5 aportan el valor Legacy que faltaba **sin
> copiar código PHP, sin reproducir bugs y sin IA**. La rama `feat/v2-migration` NO se ha
> mergeado a `main` ni se ha creado tag — pendiente de decisión del propietario.
>
> **A partir de aquí no se añaden más funcionalidades de migración.** Cualquier funcionalidad
> Legacy adicional o mejora detectada va a `BRIKKA_V2_MIGRATION_SCOPE.md §6 FUTURO` y **no se
> implementa** (en particular: IA real — narrativa verbalizada, OCR, extracción, clasificación
> documental; PDF real; contratos legales I6; tarifas por empresa; RAG en `case-list`).
