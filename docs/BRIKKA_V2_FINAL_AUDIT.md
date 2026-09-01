# BRIKKA V2 — AUDITORÍA TÉCNICA FINAL Y CIERRE

> Auditoría **de solo revisión** de la rama `feat/v2-migration` antes de un eventual merge a `main`.
> Realizada el **2026-09-01**. **No se modificó código** durante la auditoría (el único fichero
> creado es este informe).

---

## 1. Resumen ejecutivo

La migración funcional **Legacy → BRIKKA V2** cierra el alcance aprobado (`BRIKKA_V2_MIGRATION_SCOPE.md
§1`: bloques **I1–I5**; I6 fuera). La rama `feat/v2-migration` contiene **6 commits lineales** sobre
`main@5c4b223` (1 de planificación + 5 de bloque), sin merges, todos del mismo autor y en orden
cronológico y lógico correcto.

**Estado global:** los cinco bloques están implementados en backend y frontend, con validación de
dominio (contrato de error estándar `{code, message, requestId}`), aislamiento multi-tenant probado
por recurso nuevo, y batería completa en verde (backend `./mvnw clean verify` **BUILD SUCCESS**
Surefire 134 / Failsafe 466; frontend `ng lint` limpio, `ng test` 507/507, `ng build` OK).

**Hallazgos:** 0 críticos, 0 importantes, **5 menores** — todos de coherencia documental
(`CHANGELOG.md` / `12_DECISION_LOG.md` no reflejan V2 todavía; algún nombre de fichero de migración
en la fase de planificación no coincide con el definitivo; matiz de verbo para R05 entre los dos
docs de gap). Ninguno bloquea el merge.

**Veredicto:** **`READY TO MERGE`** (ver §20 para condiciones recomendadas antes del push).

---

## 2. Estado Git

| Comprobación | Resultado |
|---|---|
| Rama actual | `feat/v2-migration` |
| `git status` | limpio — `nothing to commit, working tree clean` (0 ficheros sin commitear antes de esta auditoría) |
| Cambios locales peligrosos | **ninguno** |
| `main` local | `5c4b223` (`feat(v1): client form captures employer and tenure…`) |
| `origin/main` | existe; `feat/v2-migration` **NO** está en `origin` (rama solo local, sin upstream) |
| Commits `main..HEAD` | **6**, lineales, **0 merges** |
| Autor de los 6 commits | Israel Muela |
| Marcas de tiempo | ascendentes (2026-08-31 10:38 → 16:54) |
| Tags | ninguno nuevo (solo los pre-existentes de V1: `v1.0.0`, `sprint-*`) |
| Ficheros no fuente en el diff | **ninguno** (todo `.java` / `.ts` / `.html` / `.sql` / `.md`) |
| Artefactos / `.env` / logs / generated / credenciales | **ninguno** |

No se ejecutó ningún comando destructivo (reset / clean / checkout / rebase / merge / commit de
código / push / tag).

---

## 3. Historial V2

```
5c4b223  (main)                          feat(v1): client form … (base V2-0)
  └── 3de7109  docs(v2): freeze Legacy→V2 migration scope (I1–I5) and start tracking
       └── f07c919  feat(v2): I1 — document checklist for the case (auto-generated, closed by review)
            └── 3e00857  feat(v2): I3 — business preconditions for three case transitions + authorized override
                 └── 33f4d18  feat(v2): I2 — factory scoring ruleset + case RAG indicator
                      └── cd6218c  feat(v2): I4 — simulations enriched with interest type, bonifications and ICO
                           └── cd89a49  feat(v2): I5 — case documents ZIP + deterministic dossier narrative   (HEAD)
```

- Los 6 commits **existen** y están en `feat/v2-migration`.
- **No hay commits inesperados** entre ellos.
- El orden de *sprint* (V2-1=I1, V2-2=I3, V2-3=I2, V2-4=I4, V2-5=I5) explica que I3 se commitee antes
  que I2 — coherente con `SCOPE §8` (I3 no depende de I2; ambos consumen I1).
- **Ninguna modificación posterior** altera el alcance: `git status` limpio, HEAD = `cd89a49`.
- Diff total `main...HEAD`: **110 ficheros**, +9231 / −577. Reparto: 75 backend, 31 frontend, 4 docs
  (`BRIKKA_V2_FUNCTIONAL_GAP.md`, `BRIKKA_V2_BUSINESS_RULES_GAP.md`, `BRIKKA_V2_MIGRATION_SCOPE.md`,
  `BRIKKA_V2_MIGRATION_PROGRESS.md`, todos nuevos).

---

## 4. Auditoría I1 — Checklist documental

| Requisito | Verificación | Estado |
|---|---|---|
| Seed de `document_requirements` | `V27__document_checklist.sql`: `INSERT … SELECT … JOIN document_types` con 9 requisitos `PURCHASE` (5 obl. / 4 opc.), `conditions.appliesTo` = `PER_HOLDER` \| `PER_CASE` | ✅ |
| Auto-generación | `CaseService.changeStatus` → al entrar en `DOCUMENTATION` llama `caseChecklistService.ensureRequests(...)` (idempotente, `existsForRequirement` antes de insertar) | ✅ |
| Cierre por evidencia / estado aprobado | `CaseChecklistService` deriva el estado del requisito de `documents.status`; `complete = mandatoryMissing == 0` y `mandatoryMissing` cuenta solo `state != APPROVED`. Hook `DocumentRequestFulfillment.onDocumentReviewed` reconcilia al aprobar/rechazar | ✅ |
| Vista de completitud | `GET /api/v1/cases/{caseId}/checklist` → `CaseChecklistResponse` (`mandatoryTotal/Missing`, `optionalTotal/Missing`, `complete`, `items[]`). Frontend: sección "Checklist documental" en `case-detail` con tabla documento/titular/obligatorio/estado | ✅ |
| Aislamiento tenant | `requireCaseAccess(auth, "DOCUMENT_REQUEST", caseId)`; tests `CaseChecklistServiceIT.checklistIsTenantScoped` + `DocumentEndpointsIT.checklistEndpointIsTenantScoped` (→ 404) | ✅ |
| `documents.client_id` nullable | `ALTER TABLE documents ADD COLUMN client_id uuid REFERENCES clients (id)` — **sin `NOT NULL`** → todo documento existente y todo flujo actual sigue con `client_id = NULL`. Índice `idx_documents_client_id` | ✅ |
| Sin duplicaciones Legacy innecesarias | **1 sola** columna de esquema nueva (`documents.client_id`); reutiliza `document_requirements` (V5) + `document_requests.requirement_id`. `oferta_bancaria` de Legacy **no** se recupera como documento (es `BankOffer`). Enganche "documento→requisito" aislado en `DocumentRequestFulfillment` (costura AI-ready, sin IA) | ✅ |

**Sin observaciones.** El `FK documents.client_id → clients(id)` no lleva `ON DELETE`; en la práctica
un cliente con casos no se borra (regla de negocio existente) y un documento implica un caso, así que
no genera un estado inconsistente.

---

## 5. Auditoría I2 — Scoring de fábrica + RAG

| Requisito | Verificación | Estado |
|---|---|---|
| Ruleset ACTIVE de fábrica | `V29`: `INSERT INTO scoring_rulesets (…, status, rules) VALUES (…, 'ACTIVE', '{"categories":[…]}'::jsonb)`, `code = default-operation-v1`, `version = v1` | ✅ |
| Categorías GREEN / AMBER / RED | En el **jsonb del ruleset** (`rules.categories`): `RED maxScore 40`, `AMBER 69`, `GREEN null` (catch-all). Ascendente, un único `null` al final — cumple `ScoringRulesValidator` D9-3 | ✅ |
| Reglas en datos, no en Java | 4 filas `scoring_rules` con `configuration` jsonb `{field, operator, value}` sobre los **5 campos cerrados** de `ScoreField` (`computed.ltv`, `financingRequest.termMonths`, `financingRequest.requestedAmount`). Ningún umbral en código | ✅ |
| Reutiliza el motor existente | El scoring lo produce `ScoringEngine` / `ScoringService` (D9-*, sin cambios). V29 solo aporta datos | ✅ |
| Indicador RAG | `scoring/CaseRagService.evaluate(companyId, caseId, operationType, holderIds)` → `CaseRagIndicator(level, axes)`; `GET /api/v1/cases/{caseId}/scoring/rag` (`SCORING_READ`) | ✅ |
| Combinación cualitativa | 3 ejes (`scoring`, `viability`, `documentation`) → **peor de los ejes evaluados**; `NOT_EVALUATED` no empeora; todos `NOT_EVALUATED` ⇒ `NOT_EVALUATED` (nunca degrada a verde) | ✅ |
| Sin fórmula 65/35 | Grep sobre el diff: solo aparece en comentario de `V29` como negación explícita | ✅ |
| Sin score de cliente Legacy | Idem — `calculateClientScore` / `clientScore` no existen en V2 | ✅ |
| Sin gate numérico | `CaseTransitionPreconditions` no consulta ningún score; el RAG es solo lectura, no bloquea transiciones | ✅ |
| Aislamiento tenant | Ejes `scoring` y `viability` filtran `result.companyId().equals(companyId)`; endpoint vía `requireCaseAccess`. Tests: `CaseRagServiceIT.scoringResultOfAnotherTenantNeverLeaksIntoTheIndicator`, `ScoringEndpointsIT.ragFromAnotherTenantCaseIsNotFound` (→ 404) | ✅ |

`ScoringNoActiveRulesetIT` se adaptó (desactiva el ruleset de fábrica por SQL antes de comprobar el
guard `NO_ACTIVE_SCORING_RULESET`) — adaptación correcta y esperada, no debilita nada.

**Sin observaciones.**

---

## 6. Auditoría I3 — Precondiciones de transición

| Requisito | Verificación | Estado |
|---|---|---|
| Gate 1 `DOCUMENTATION → ANALYSIS` | `requireMandatoryDocumentsApproved` → `caseChecklistService.checklist(...).complete()` (I1); si no, `PRECONDITION_CHECKLIST_INCOMPLETE` (400) | ✅ |
| Gate 2 `BANK_SEARCH → BANK_SUBMISSION` | `bankRequestRepository.existsByCaseIdAndCompanyId(caseId, companyId)`; si no, `PRECONDITION_NO_BANK_REQUEST` | ✅ |
| Gate 3 `OFFER → FORMALIZATION` | `finalFinancingRepository.findByCaseId(...).filter(ff.companyId == theCase.companyId)` + la `bank_offer_id` debe pertenecer a una oferta del caso; si no, `PRECONDITION_NO_SELECTED_OFFER` | ✅ |
| **Sin gates adicionales** | `check()` tiene exactamente esos 3 `if`; comentario: *"Any other transition has no I3 precondition — deliberately untouched"*. El check pre-existente `≥1 cliente antes de DOCUMENTATION` **NO fue añadido por I3** (verificado: no está en el diff de `CaseService.java`) | ✅ |
| Override autorizado | `changeStatus(..., overridePreconditions)`: si `true`, salta los gates y exige `reason` no vacío (`PRECONDITION_OVERRIDE_REASON_REQUIRED`) | ✅ |
| Permiso `CASE_TRANSITION_OVERRIDE` | `V28`: 1 permiso + 2 `role_permissions` (**MANAGER, SUPERADMIN** — mismo criterio que `BANK_MATCHING_OVERRIDE`). `CaseController` línea 139: `if (override) authorizationService.requirePermission(auth, "CASE_TRANSITION_OVERRIDE")` → 403 sin permiso | ✅ |
| Motivo obligatorio + registro | `case_status_history.reason` con marcador `"[PRECONDITION_OVERRIDE] "` | ✅ |
| Auditoría existente | Evento `CASE_STATUS_CHANGED` + payload con `"override":true`; **sin auditoría paralela** | ✅ |
| Aislamiento tenant | Gates 2/3 con `companyId`; tests `CaseTransitionPreconditionsIT.gate2_bankRequestOfAnotherTenantDoesNotSatisfyTheGate`, `gate3_selectedOfferOfAnotherTenantDoesNotSatisfyTheGate` | ✅ |
| Contadores RBAC | `FlywayMigrationIT` 116→117 permisos, 250→252 `role_permissions`, `caseTransitionOverrideGrants == 2`; `RbacSeedIT` 252/117/92(SUPERADMIN)/82(MANAGER); `IdentityEndpointsIT` MANAGER 82 | ✅ |

**Sin observaciones.** Es el **único** bloque que añade un permiso (1), justificado y correctamente
asignado.

---

## 7. Auditoría I4 — Simulación enriquecida

| Requisito | Verificación | Estado |
|---|---|---|
| FIXED / VARIABLE / MIXED | `SimulationInterestType` (enum, CHECK en `V30`); `SimulationService.buildInterestModel` valida los campos aplicables por tipo | ✅ |
| Euríbor / diferencial | `euribor_rate` / `spread_rate` `numeric(7,4)` (VARIABLE + MIXED); el euríbor **puede ser negativo** (validado y testeado), el diferencial no | ✅ |
| Tramo fijo (MIXED) | `fixed_period_months integer CHECK (> 0)` + `fixed_period_rate numeric(7,4)`; validación `fixedPeriodMonths ∈ [1, plazo − 1]` (`INVALID_SIMULATION_FIXED_PERIOD`) | ✅ |
| Bonificaciones | `bonifications jsonb` (array `{code,label,rate,active}` — **datos**, no seis códigos en Java). `SimulationBonificationCatalog` = solo etiquetas de los conocidos; se admiten códigos no listados con descripción | ✅ |
| Tipo base / tipo final | `base_interest_rate` / `final_interest_rate` `numeric(7,4)` (`NOT NULL` tras backfill). `final = max(0, base − Σ bonif. activas)` — *floor* 0, **aplicado de verdad** (Legacy no lo hacía) | ✅ |
| Cuota calculada por backend | `estimated_payment` deja de teclearla el bróker; `SimulationInterestCalculator` la calcula vía `MortgagePaymentCalculator.computeMonthlyPayment`. MIXED: cuota del tramo fijo + cuota del tramo variable re-amortizando el saldo pendiente (`computeOutstandingBalance`, nueva) | ✅ |
| ICO | `ico_guarantee boolean NOT NULL DEFAULT false` en **`simulations`** (decisión de diseño documentada: el aval aplica al escenario de financiación; `cases` no tiene esa columna en la Brikka moderna). Solo representa el dato, sin motor de elegibilidad | ✅ |
| BigDecimal | `SimulationInterestCalculator` y `MortgagePaymentCalculator`: **sin `double` / `float`**, sin `Math.pow` (usa `BigDecimal.pow`), `RoundingMode.HALF_UP` explícito (escala 4 tipos, escala 2 dinero) | ✅ |
| Cálculo MIXED | Dos tramos; `interest_rate` (lo que consumen aguas abajo) = tipo del tramo fijo (el que rige al inicio); fase variable **re-derivada al leer** (determinista) | ✅ |
| **Sin segundo motor de cálculo** | `SimulationInterestCalculator` **orquesta** `MortgagePaymentCalculator`; este se **movió** de `financialanalysis` a `financing` (evita ciclo de paquetes) — es la misma clase (git `R055`), consumidor único `FinancialAnalysisService` actualiza el `import` | ✅ |
| Bugs Legacy descartados | `V30` comentario: `monthly_payment_phase2`, `total_interest`, `recommended` **NO** se recuperan. Grep confirma que no existen en V2 | ✅ |
| Aislamiento tenant | `POST /simulations` vía `requireCaseAccess`; test `FinancingEndpointsIT.simulationFromAnotherTenantCaseIsNotFound` (→ 404) | ✅ |

**Sin observaciones.** El flag ICO en `simulations` (no en `cases`) es una decisión de diseño dentro
de la latitud que da `SCOPE §1 I4` ("`cases` u `properties` — decisión de diseño dentro del sprint");
se resolvió a `simulations` y quedó documentado.

---

## 8. Auditoría I5 — Dossier + ZIP

| Requisito | Verificación | Estado |
|---|---|---|
| ZIP streaming | `GET /api/v1/cases/{caseId}/documents/archive` → `ResponseEntity<StreamingResponseBody>`. `CaseDocumentsArchiveService.writeArchive` abre **un `InputStream` de documento a la vez** (`StorageClient.openStream` nuevo → `s3Client.getObject`) y lo copia con `InputStream.transferTo(zip)` a un `ZipOutputStream` sobre la respuesta | ✅ |
| Sin temporales | No hay `File.` / `Files.` / `createTempFile`; no hay `toByteArray()` / `readAllBytes()` sobre el archivo completo. Prueba unitaria: máx. **1 stream abierto** con 25 documentos, todos cerrados | ✅ |
| Documentos actuales | Se incluye `documents.current_version_id` de cada `documents` del caso con versión subida — la misma versión que `GET /documents/{id}/download`. Sin filtro de aprobación/publicación (documentado). Versionado inmutable **intacto** (solo lectura) | ✅ |
| Estructura interna | `<tipo>/<titular \| "expediente">/<documentId>-<nombre original>` — **derivada de metadatos actuales, NO de las carpetas Legacy `01–06`** | ✅ |
| Path traversal | Cada segmento pasa por `SafeFilenames.sanitize` (hecho `public`; reemplaza `/` `\` → `_`, deja solo `[A-Za-z0-9._-]`, corta a 200) **+** guarda: segmento vacío / `.` / `..` → `_`. Prefijo `documentId` hace único cada nombre. Test con metadatos hostiles (`../../etc/passwd`, titular con comillas y `../..`): ningún segmento es `.` ni `..`, ninguna ruta empieza por `/` | ✅ |
| Control de acceso | `requireCaseAccess(auth, "DOCUMENT_DOWNLOAD", caseId)` **antes** de tocar ningún documento. Sin permiso nuevo | ✅ |
| Tenant isolation | Otro tenant → **404** (`caseDocumentsArchiveFromAnotherTenantIsNotFound`). Nunca incluye documentos de otro caso (`caseDocumentsArchiveNeverIncludesAnotherCasesDocuments`). Guarda por documento `document.companyId().equals(companyId)` | ✅ |
| Caso sin documentos | `countDownloadable == 0` → `400 CASE_HAS_NO_DOCUMENTS` **antes** de arrancar el cuerpo (JSON limpio) | ✅ |
| Errores en streaming | Acceso + count antes del cuerpo → 403/404/400 son JSON. Fallo de almacenamiento a mitad del stream → `log.warn` + `UncheckedIOException` (patrón estándar de `StreamingResponseBody`, sin sistema de errores nuevo) | ✅ |
| Narrativa determinista | `dossier/CaseNarrativeService.narrate(Case) → CaseNarrative`. Sin IA, sin `import com.brika.platform.ai.*`, sin `AiProvider`, sin Ollama, sin lectura de reloj en el texto. Test `narrativeIsDeterministic` + test HTTP de determinismo | ✅ |
| 8 secciones | `situation`, `holders`, `property`, `financing`, `scoring`, `viability`, `documentation`, `fees` — siempre presentes; `CaseNarrativeServiceIT.fullyInformedCaseHasEverySectionPopulated` lo asserta con `containsExactly(...)` | ✅ |
| Reutiliza el dossier existente | `ViabilityDossierService` **reescrito** (389 → ~130 líneas): su HTML es ahora la narrativa sección a sección. Mismo endpoint `POST/GET /dossier`, mismo `Document` versionado `VIABILITY_DOSSIER`, mismo snapshot, mismo `NO_CLIENTS_ON_CASE`. `GET /dossier/narrative` (solo lectura, `DOCUMENT_READ`). **No hay segundo dossier** | ✅ |
| Sin IA / sin segundo motor de dossier | Confirmado por grep y por lectura | ✅ |

**Sin observaciones.**

---

## 9. Seguridad

### Multi-tenant

Todos los accesos a datos introducidos por V2 resuelven el tenant desde la identidad autenticada
(`CaseAccessService.requireCaseAccess` / `DocumentAccessService.requireDocumentAccess`), nunca desde
un `company_id` del cliente. Filtrado explícito por `company_id` donde el repositorio no lo hace:

| Recurso | Filtro | Test |
|---|---|---|
| Checklist (I1) | caso ya validado; `document_requests` por `caseId` | `checklistIsTenantScoped`, `checklistEndpointIsTenantScoped` |
| Scoring result / RAG (I2) | `result.companyId().equals(companyId)` en `CaseRagService` | `scoringResultOfAnotherTenantNeverLeaksIntoTheIndicator`, `ragFromAnotherTenantCaseIsNotFound` |
| Bank request / final financing (I3) | `existsByCaseIdAndCompanyId`, `filter(ff.companyId == theCase.companyId)` | `gate2_…AnotherTenant…`, `gate3_…AnotherTenant…` |
| Simulación (I4) | caso validado; `POST /simulations` vía `requireCaseAccess` | `simulationFromAnotherTenantCaseIsNotFound` |
| ZIP documentos (I5) | `requireCaseAccess` + `document.companyId().equals(companyId)` por documento | `caseDocumentsArchiveFromAnotherTenantIsNotFound`, `caseDocumentsArchiveNeverIncludesAnotherCasesDocuments` |
| Narrativa (I5) | `scoring` / `viability` filtrados por `companyId`; endpoint vía `requireCaseAccess` | `anotherTenantScoringAndViabilityNeverAppear`, `narrativeFromAnotherTenantIsNotFound` |

### IDOR

- Manipular `caseId` en cualquiera de los 8 endpoints V2 → `requireCaseAccess` enmascara como **404**
  para otro tenant (probado por los tests `*FromAnotherTenantIsNotFound`).
- El endpoint del ZIP toma **solo `caseId`** (no `documentId`) → un llamante no puede seleccionar un
  documento ajeno vía el ZIP; el servicio solo itera documentos del caso validado.
- Manipular `documentId` → `requireDocumentAccess` resuelve el caso transitivamente y enmascara
  (mecanismo pre-existente, sin cambios).

### Permisos usados por V2

| Permiso | Uso V2 | ¿Nuevo? | Roles |
|---|---|---|---|
| `DOCUMENT_REQUEST` | `GET /checklist` (I1) | no | SUPERADMIN/MANAGER/BROKER |
| `DOCUMENT_DOWNLOAD` | `GET /documents/archive` (I5) | no | SUPERADMIN/MANAGER/BROKER |
| `DOCUMENT_READ` | `GET /dossier/narrative` (I5) | no | SUPERADMIN/MANAGER/BROKER |
| `SCORING_READ` | `GET /scoring/rag` (I2) | no | SUPERADMIN/MANAGER/BROKER |
| `SCORING_RUN` | botón "Calcular scoring" (I2, reusa `POST /scoring/run`) | no | SUPERADMIN/MANAGER/BROKER |
| `SIMULATION_CREATE` / `SIMULATION_READ` | `POST` / `GET /simulations` (I4) | no | — |
| `CASE_TRANSITION_OVERRIDE` | forzar transición (I3) | **sí (V28)** | **MANAGER, SUPERADMIN** |

**Un único permiso nuevo** en toda V2 (`CASE_TRANSITION_OVERRIDE`), justificado y correctamente
sembrado. `FlywayMigrationIT` / `RbacSeedIT` / `IdentityEndpointsIT` verifican los contadores
(117 permisos, 252 `role_permissions`). SUPERADMIN/MANAGER/BROKER mantienen su comportamiento
esperado; ningún permiso pre-existente cambia de asignación.

### Excepción `CASE_TRANSITION_OVERRIDE`

Requiere **permiso** (`CaseController` → `requirePermission` → 403 sin él) **y motivo no vacío**
(`CaseService` → `PRECONDITION_OVERRIDE_REASON_REQUIRED`). El motivo se persiste con marcador en
`case_status_history.reason` y el evento de auditoría lleva `"override":true`. Correcto.

**Sin hallazgos de seguridad.**

---

## 10. API

Endpoints introducidos o modificados por V2 (todos bajo `requireCaseAccess` = tenant + rol +
asignación de BROKER):

| # | Método | URL | Permiso | Bloque | Estado | Errores | Request DTO | Response DTO |
|---|---|---|---|---|---|---|---|---|
| 1 | GET | `/api/v1/cases/{caseId}/checklist` | `DOCUMENT_REQUEST` | I1 | **nuevo** | 403/404 | — | `CaseChecklistResponse` |
| 2 | POST | `/api/v1/cases/{caseId}/documents` | `DOCUMENT_CREATE` | I1 | **mod** (`+clientId?`) | 400 `CLIENT_NOT_ON_CASE`, 403/404 | `CreateDocumentApiRequest(documentTypeId, clientId?)` | `DocumentResponse` (`+clientId`) |
| 3 | GET | `/api/v1/cases/{caseId}/scoring/rag` | `SCORING_READ` | I2 | **nuevo** | 403/404 | — | `CaseRagResponse(rag, axes[])` |
| 4 | POST | `/api/v1/cases/{id}/status` | `CASE_CHANGE_STATUS` (+`CASE_TRANSITION_OVERRIDE` si `override`) | I3 | **mod** (`+override?`) | 400 `PRECONDITION_*` / `INVALID_TRANSITION` / `PRECONDITION_OVERRIDE_REASON_REQUIRED`, 403/404 | `ChangeCaseStatusApiRequest(newStatus, reason, override?)` | `Case` |
| 5 | POST | `/api/v1/cases/{caseId}/simulations` | `SIMULATION_CREATE` | I4 | **mod** (DTO enriquecido) | 400 `INVALID_SIMULATION_*` / `SIMULATION_INTEREST_MODEL_MISMATCH` / `NEGATIVE_SIMULATION_VALUE`, 403/404 | `CreateSimulationApiRequest(interestType, principal, termMonths, fixedRate?, euriborRate?, spreadRate?, fixedPeriodMonths?, fixedPeriodRate?, bonifications[], icoGuarantee?, metadata)` | `SimulationResponse` (+desglose + `variablePhase?`) |
| 6 | GET | `/api/v1/cases/{caseId}/simulations` | `SIMULATION_READ` | I4 | **mod** (respuesta) | 403/404 | — | `SimulationResponse[]` |
| 7 | GET | `/api/v1/cases/{caseId}/documents/archive` | `DOCUMENT_DOWNLOAD` | I5 | **nuevo** | **400 `CASE_HAS_NO_DOCUMENTS`**, 403/404; fallo de streaming aborta la descarga | — | `application/zip` (stream) + `Content-Disposition` |
| 8 | GET | `/api/v1/cases/{caseId}/dossier/narrative` | `DOCUMENT_READ` | I5 | **nuevo** | 403/404 | — | `CaseNarrativeResponse(sections[])` |

**No hay APIs paralelas.** Cada bloque extiende el controlador existente
(`DocumentController`, `SimulationController`, `CaseController`, `ViabilityDossierController`) o añade
uno acotado al mismo dominio (`CaseRagController`, `CaseChecklistController`). Contrato de error
`{code, message, requestId}` respetado en todos los 4xx.

---

## 11. Base de datos

| Migración | Contenido | Orden | Aditiva / no destructiva | Constraints / defaults | Idempotencia/reproducibilidad |
|---|---|---|---|---|---|
| `V27__document_checklist.sql` | `UNIQUE(operation_type, document_type_id)` en `document_requirements`; `documents.client_id uuid NULL REFERENCES clients(id)` + índice; seed de 9 requisitos `PURCHASE` | tras V26 | ✅ (solo `ADD` + `INSERT`) | FK a `clients(id)`; `client_id` **nullable**; UNIQUE | migración versionada; seed por `INSERT…SELECT…JOIN` sobre catálogo controlado |
| `V28__case_transition_override_permission.sql` | 1 `permissions` + 2 `role_permissions` (MANAGER, SUPERADMIN) | tras V27 | ✅ (solo `INSERT`) | — | `gen_random_uuid()` + JOIN por `code` |
| `V29__seed_default_scoring_ruleset.sql` | 1 `scoring_rulesets` ACTIVE + 4 `scoring_rules` | tras V28 | ✅ (solo `INSERT`) | categorías jsonb validadas por `ScoringRulesValidator` | `code`/`version` fijos; JOIN por `code` |
| `V30__simulation_interest_type_and_bonifications.sql` | 9 columnas nuevas en `simulations`; `UPDATE` backfill; `SET NOT NULL` en base/final tras backfill | tras V29 | ✅ (`ADD COLUMN`, ningún `DROP` / cambio de tipo de columna existente) | `CHECK interest_type IN (FIXED,VARIABLE,MIXED)`; `CHECK fixed_period_months IS NULL OR > 0`; `NOT NULL DEFAULT 'FIXED'` / `false` / `'[]'::jsonb`; `base/final NOT NULL` tras backfill | migración versionada; backfill cubre todas las filas históricas |

- **Orden Flyway:** `V1 … V26` (pre-V2) → `V27 → V28 → V29 → V30`. Sin huecos, sin renumeraciones,
  sin colisiones. **Número final de migraciones = 30**, coincide con `FlywayMigrationIT`
  (`assertThat(appliedMigrations).isEqualTo(30)`) y con `BRIKKA_V2_MIGRATION_PROGRESS.md`.
- **Ninguna migración destructiva** (ningún `DROP TABLE` / `DROP COLUMN` / `ALTER COLUMN … TYPE` /
  `TRUNCATE` / `DELETE` sobre datos existentes).
- **Compatibilidad con datos existentes:** V27 (`client_id` nullable), V30 (backfill FIXED con base =
  final = `interest_rate`, `estimated_payment` intacto). Ninguna conversión silenciosa.
- `FlywayMigrationIT` verifica además: 63 tablas (sin cambio — ninguna migración V2 añade tabla),
  seed V29 (1 ruleset ACTIVE, 4 reglas, 3 categorías), columnas V30 (9, `interest_type` NOT NULL,
  `ico_guarantee` default `false`).

---

## 12. Frontend

| Área | Cambio V2 | Estado |
|---|---|---|
| Permisos / guards | Todas las secciones/botones nuevos usan `*appHasPermission` con el permiso exacto del backend (`DOCUMENT_REQUEST`, `SCORING_READ`, `SCORING_RUN`, `DOCUMENT_DOWNLOAD`, `CASE_TRANSITION_OVERRIDE`) | ✅ |
| Manejo de errores | `friendlyErrorMessage(err)` + `error-messages.ts` (única fuente de traducción); nuevos códigos: `PRECONDITION_*` (I3), `NO_ACTIVE_SCORING_RULESET` (I2), `INVALID_SIMULATION_*` / `SIMULATION_INTEREST_MODEL_MISMATCH` / `NEGATIVE_SIMULATION_VALUE` (I4), `CASE_HAS_NO_DOCUMENTS` (I5). El 400 del ZIP se traduce a nivel de componente (respuesta blob) | ✅ |
| Traducciones | Todo texto nuevo en español; enums vía mapas `*_LABELS` en `status-labels.ts` (`RAG_LEVEL_LABELS`, `RAG_AXIS_LABELS`, `INTEREST_TYPE_LABELS`); `statusTone` mapea `GREEN/AMBER/RED` (conjunto cerrado, antes de la heurística léxica). Sin texto de usuario sin traducir | ✅ |
| Modelos | `scoring.model.ts` (I2), `financing.model.ts` enriquecido (I4), `viability-dossier.model.ts` +`CaseNarrative` (I5), `document.model.ts` +checklist (I1), `case.model.ts` +`override?` (I3) — todos "mirror" del backend | ✅ |
| Servicios | `scoring.service.ts` (I2), `case-archive.service.ts` (I5), `ApiClient.getBlob` (I5), `viability-dossier.service.ts` +`getNarrative` (I5), `financing.service.ts` (I4), `documents.service.ts` +`getChecklist` (I1). Sin lógica de negocio, thin wrappers | ✅ |
| Componentes / diálogos | `create-simulation-dialog` (campos adaptados al tipo, bonificaciones, ICO, previsualización), `change-status-dialog` (casilla forzar), `create-document-dialog` (selector titular), `case-detail` (checklist, RAG, columna "Tipo", panel narrativa, botón ZIP) | ✅ |
| Descarga ZIP | `ApiClient.getBlob` (`responseType: 'blob'`, `observe: 'response'`) → el interceptor de auth adjunta el bearer; blob → `URL.createObjectURL` + `<a download>` + `URL.revokeObjectURL` (par correcto, sin fuga de object URL) | ✅ |
| Narrativa | Panel de solo lectura en `case-detail` (`<h4>` + `<p>` por sección) | ✅ |
| Scoring/RAG | Sección "Indicador RAG" con badge global + tabla de 3 ejes (envuelta en `table-scroll`, sin overflow) | ✅ |
| Simulaciones | Diálogo con campos condicionales; **la cuota la calcula el servidor** — la previsualización cliente es aritmética trivial (`base`, `Σ`, `final = max(0, base − Σ)`), **sin fórmula de amortización duplicada** (comentario explícito) | ✅ |

**Búsquedas específicas:**
- **Llamadas HTTP duplicadas:** no detectadas. Las cargas de `case-detail` en el constructor son una
  por recurso; el patrón de `.subscribe()` sobre observables de `HttpClient` (que completan) es el
  ya existente en el componente, no introduce fuga.
- **Lógica de negocio duplicada / cálculo financiero incorrecto en frontend:** no. La única
  aritmética financiera del frontend es la resta de tipos para la previsualización; el pago y el
  tipo efectivo son del backend.
- **Estados imposibles / subscriptions innecesarias / fugas de memoria:** no detectadas.
- **UX rota por overflow:** tablas anchas envueltas en `table-scroll` (patrón existente).
- **Textos sin traducción:** ninguno.

`ng lint` limpio · `ng test` 507/507 · `ng build` genera el bundle sin error.

---

## 13. Tests

### Existencia verificada (los descritos en los informes existen realmente)

| Bloque | Tests unit | Tests IT | Tenant isolation | Permisos / negativos |
|---|---|---|---|---|
| I1 | — | `CaseChecklistServiceIT` (6), `DocumentEndpointsIT` (+2) | `checklistIsTenantScoped`, `checklistEndpointIsTenantScoped` | subir ≠ completo, aprobar → completo, rechazar reabre |
| I2 | `ScoringEngineTest`, `ScoringRulesValidatorTest` (existentes) | `CaseRagServiceIT` (7), `ScoringEndpointsIT` (+2), `ScoringNoActiveRulesetIT` (adaptado) | `scoringResultOfAnotherTenantNeverLeaksIntoTheIndicator`, `ragFromAnotherTenantCaseIsNotFound` | `NO_ACTIVE_SCORING_RULESET`, categoría desconocida → `NOT_EVALUATED` |
| I3 | — | `CaseTransitionPreconditionsIT` (15), `CrmCaseEndpointsIT` (+2), `CaseServiceIT`, `CaseChecklistServiceIT` | `gate2_…AnotherTenant…`, `gate3_…AnotherTenant…` | los 3 gates bloqueados/permitidos/con override; override sin motivo → rechazado; BROKER sin permiso → 403 |
| I4 | `SimulationInterestCalculatorTest` (7), `SimulationServiceValidationTest` (12), `MortgagePaymentCalculatorTest` (+5) | `FinancingEndpointsIT` (11) | `simulationFromAnotherTenantCaseIsNotFound` | 7 códigos de validación; FIXED con euríbor / VARIABLE sin diferencial / MIXED tramo fijo ≥ plazo / bonif. duplicada/negativa/en blanco |
| I5 | `CaseDocumentsArchiveServiceTest` (2) | `CaseNarrativeServiceIT` (7), `CaseNarrativeEndpointsIT` (5), `DocumentEndpointsIT` (+5) | `caseDocumentsArchiveFromAnotherTenantIsNotFound`, `caseDocumentsArchiveNeverIncludesAnotherCasesDocuments`, `anotherTenantScoringAndViabilityNeverAppear`, `narrativeFromAnotherTenantIsNotFound` | sin documentos → 400; BROKER sin asignación → 403; streaming (1 stream a la vez); nombres seguros; narrativa determinista |

### Batería completa (estado final)

| Suite | Resultado |
|---|---|
| Backend `./mvnw clean verify` (commit `cd89a49`, árbol idéntico) | **BUILD SUCCESS** — Surefire **134/134**, Failsafe **466/466**, 0 fallos / 0 errores; `spotless:check` ✅ |
| Backend `./mvnw clean verify` (re-ejecución de auditoría, 2026-09-01, árbol sin cambios) | **BUILD SUCCESS** — Surefire **134/134**, Failsafe **466/466**, 0 fallos / 0 errores (el `WARN` de Surefire "going to kill self fork JVM" es el apagado normal tras `System.exit(0)`, no un fallo) |
| Frontend `ng lint` | **All files pass linting** |
| Frontend `ng test` | **507 passed (507)** — 101 ficheros |
| Frontend `ng build` | **Application bundle generation complete** (sin error) |

Ningún test fue modificado para hacerlo pasar. `ScoringNoActiveRulesetIT` se adaptó como parte de I2
(desactiva el ruleset de fábrica por SQL) — es una adaptación legítima, no un parche para ocultar un
fallo.

---

## 14. Scope creep

Búsqueda específica sobre el diff `main...HEAD` (código main + test) de las funcionalidades
**expresamente fuera de V2**:

| Buscado | Resultado |
|---|---|
| IA real / `AiProvider` / Ollama / `import com.brika.platform.ai` | **no introducido** (solo menciones negativas en javadoc de `CaseNarrativeService`) |
| OCR / extracción / clasificación automática | **no** |
| Firma electrónica | **no** |
| PDF real / dompdf / Gotenberg / OpenPDF / Flying Saucer / iText | **no** |
| Contratos legales I6 | **no** |
| Tarifas por empresa / facturación | **no** |
| Criterios bancarios (nuevos) | **no** |
| Scoring Legacy de cliente / `calculateClientScore` / ponderación 65/35 | **no** (solo negación explícita en comentario de `V29`) |
| Gates de transición numéricos | **no** |
| Carpetas físicas Legacy `01–06` | **no** (la ruta del ZIP se deriva de metadatos) |
| `mortgage_holders` | **no** |
| `recommended` / `monthly_payment_phase2` / `total_interest` | **no** (solo negación explícita en comentario de `V30`) |
| Ficheros de config / `.env` / `.yml` / `.properties` | **ninguno modificado** |

**Sin scope creep.**

---

## 15. Comparación final con Legacy

Referencias: `BRIKKA_V2_FUNCTIONAL_GAP.md`, `BRIKKA_V2_BUSINESS_RULES_GAP.md`,
`BRIKKA_V2_MIGRATION_SCOPE.md`. **No se re-implementó la Legacy**; solo se comprueba el destino de
cada hueco `FALTA` / `PARCIAL`.

| Fila GAP | Estado original | Destino | Verificado |
|---|---|---|---|
| 10a | PARCIAL | **I3** (3 precondiciones) | ✅ implementado |
| 10b, 11a, 11b | FALTA | **DESCARTADO** (§10.1 — umbrales / ponderación sin base) | ✅ descartado conscientemente |
| 11c | PARCIAL | **I2** (RAG cualitativo) | ✅ mejorado |
| 11d | FALTA | **I2** (`V29` seed) | ✅ implementado |
| 12 | PARCIAL | **I1** | ✅ mejorado |
| 16 | FALTA | **DESCARTAR** (declarado en el propio gap; se solapa con el dossier) | ✅ descartado |
| 17, 17a, 17b | PARCIAL/FALTA | **I4** | ✅ implementado / mejorado |
| 17e | PARCIAL | **FUTURO** (`SCOPE §6.5` — seed de ~45 bancos) | ✅ registrado |
| 18, 18a, 21, 22 | PARCIAL/FALTA | **I5** | ✅ implementado / mejorado |
| 4a, 4c, 14, 14a, 14b | FALTA/PARCIAL | **FUTURO** (I6 y dependencias, §10.4/§10.6) | ✅ registrado |
| 24, 25 | FALTA/PARCIAL | **FUTURO** (`SCOPE §6.5` — condición/ubicación de inmueble); tipo de inmueble → catálogo frontend ya existente | ✅ registrado |

**Reglas de negocio** (`BUSINESS_RULES_GAP §Resumen`): `ADOPTAR-REVISADA` R12→I2, R13/R14→I1,
R18/R19→I4, R08-revisada→I3 (3 gates) — **todas implementadas**. `DESCARTAR` R08 (gate numérico),
R09 (score de cliente), R11 (65/35), R15 (replicación física), R16 (importes fijos de tarifa) —
**descartadas y documentadas**. `FUTURO` R03, R05, R21, R22, R23.

Conclusión: **todo hueco `FALTA` / `PARCIAL` quedó implementado, mejorado, descartado
conscientemente o registrado como FUTURO.** Sin huecos huérfanos.

---

## 16. Documentación

| Documento | Coherencia con el estado real |
|---|---|
| `BRIKKA_V2_FUNCTIONAL_GAP.md` | ✅ (matriz de 30 funcionalidades + §10 decisiones del propietario; concuerda con lo implementado) |
| `BRIKKA_V2_BUSINESS_RULES_GAP.md` | ✅ salvo un matiz menor (ver M-5) |
| `BRIKKA_V2_MIGRATION_SCOPE.md` | ✅ (§1 I1–I5 con marcas "Realizado", §6 FUTURO, §7 con "MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA" + tabla de commits, §8 sprint table con ✅) |
| `BRIKKA_V2_MIGRATION_PROGRESS.md` | ✅ (barra 100 %, bloques de cierre por sprint, tabla de tests, registro cronológico, Declaración de cierre con los 5 hashes de commit) |
| `CHANGELOG.md` | ⚠️ **no refleja V2** (ver M-1) — sigue en `[1.0.0]`, "26 migraciones" |
| `docs/12_DECISION_LOG.md` | ⚠️ **no refleja V2** (ver M-2) — último registro = cierre de V1.0.0 |

Contradicciones detectadas → **reportadas, no corregidas** (§17).

---

## 17. Incidencias / hallazgos

### Críticos — 0

Ninguno.

### Importantes — 0

Ninguno.

### Menores — 5 (todos de coherencia documental; no bloquean el merge)

| ID | Descripción | Propuesta de corrección (NO aplicada) |
|---|---|---|
| **M-1** | `CHANGELOG.md` no tiene sección para V2: sigue describiendo `[1.0.0]` y dice "26 migraciones" (V2 lleva a 30). | Añadir una entrada `[Unreleased]` / `[2.0.0]` con I1–I5 y "30 migraciones" **en el momento del merge/release** (es un doc release-facing y V2 está sin mergear). |
| **M-2** | `docs/12_DECISION_LOG.md` no registra las decisiones de V2 (alcance §10, los 5 sprints). | Añadir el bloque narrativo de V2 al decision log en el merge, con el mismo estilo que los sprints de V1. |
| **M-3** | `BRIKKA_V2_MIGRATION_PROGRESS.md` línea 516 (DoD de I1, redacción de planificación) cita `V27__seed_document_requirements.sql`; el fichero real es `V27__document_checklist.sql` (nombre correcto en las líneas 408 y 511). | Corregir el nombre en la línea 516. Cosmético. |
| **M-4** | `BRIKKA_V2_MIGRATION_SCOPE.md` línea 391 (tabla de sprints, fase de planificación) cita `V27__seed_document_requirements.sql`; idem para el nombre planificado de otras migraciones que luego cambiaron. | Nota aclaratoria o corrección de los nombres planificados a los definitivos (`V27__document_checklist.sql`, etc.). Cosmético. |
| **M-5** | R05 (unicidad parcial de `document_number`) figura como `ADOPTAR-REVISADA` en `BUSINESS_RULES_GAP.md` (línea 76) pero como `FUTURO` en `SCOPE §4.3` / `§6`. La decisión final del propietario (`§10.9`: alcance = I1–I5) supersede la recomendación del analista → R05 = FUTURO. | Alinear el verbo de R05 en `BUSINESS_RULES_GAP.md` a `FUTURO` (o añadir "→ FUTURO por §10.9"). No hay impacto de código: R05 nunca estuvo en I1–I5. |

---

## 18. Riesgos

| Riesgo | Nivel | Mitigación |
|---|---|---|
| `feat/v2-migration` no está en `origin` ni tiene CI ejecutada | Bajo | La batería local completa (`./mvnw clean verify` + `ng test/lint/build`) está verde. Recomendado: push de la rama y esperar CI verde **antes** del merge (igual que se hizo en V1, Gate 24). |
| `ViabilityDossierService` reescrito (−312 líneas): riesgo de regresión en el dossier | Bajo | `ViabilityDossierEndpointsIT` (8/8) verde; el endpoint, la persistencia versionada y `NO_CLIENTS_ON_CASE` no cambian. |
| `MortgagePaymentCalculator` movido de paquete | Muy bajo | Consumidor único (`FinancialAnalysisService`) actualizado; `FinancialAnalysisEndpointsIT` (10/10) verde; git lo registra como rename (`R055`). |
| El ZIP incluye el dossier/contrato HTML generados | Muy bajo | Es coherente con "toda la documentación del expediente" (son `documents` del caso). Documentado. |
| `V30` hace `SET NOT NULL` tras backfill | Muy bajo | El `UPDATE` backfill cubre todas las filas históricas; nuevas filas siempre las pone el servicio. Probado con `FlywayMigrationIT`. |
| `CHANGELOG` / `DECISION_LOG` desactualizados llegan a `main` | Bajo | Actualizarlos como parte del PR de merge (M-1, M-2). |

Ninguno de estos riesgos es bloqueante.

---

## 19. Recomendaciones

1. **Antes del merge:** push de `feat/v2-migration` a `origin` y confirmar **CI verde** (build +
   tests + lint + imagen Docker + Trivy), replicando el gate de release de V1.
2. **En el PR de merge:** resolver M-1 y M-2 — añadir la sección de V2 a `CHANGELOG.md`
   (I1–I5, "30 migraciones") y a `docs/12_DECISION_LOG.md`.
3. **Cosmético (opcional, no bloquea):** M-3, M-4 (nombres de migración en textos de planificación),
   M-5 (verbo de R05).
4. **Estrategia de merge:** dado que son 6 commits limpios y bien delimitados por bloque, un
   **merge commit** (no squash) preserva la trazabilidad I1→I5 que exige el propio encargo. No hacer
   rebase (reescribiría hashes ya referenciados en la documentación).
5. **Tag:** crear `v2.0.0` (o equivalente) **solo tras** el merge y con CI verde confirmada, con
   confirmación explícita del propietario (gate de V1).
6. **Post-merge:** revisar que `BRIKKA_V2_MIGRATION_SCOPE.md §6 FUTURO` es la lista viva de trabajo
   pendiente (IA real, PDF, I6, tarifas por empresa, seed de bancos, campos de inmueble, RAG en
   `case-list`, R05).

---

## 20. Veredicto final

Se cumplen todas las condiciones de `§13` del encargo:

- [x] I1–I5 completos (backend + frontend + validación + tests).
- [x] Sin funcionalidades V2 pendientes.
- [x] Sin regresiones (regresión de dossier / contrato / documentos / e2e / financialanalysis /
      scoring / simulación en verde).
- [x] Tests verdes: Surefire 134/134, Failsafe 466/466, `ng test` 507/507, `ng lint` OK,
      `ng build` OK.
- [x] Seguridad correcta (0 hallazgos).
- [x] Aislamiento multi-tenant correcto (probado por recurso nuevo, → 404 para otro tenant).
- [x] Permisos correctos (1 permiso nuevo justificado; contadores RBAC verificados).
- [x] Flyway correcto (V27–V30, orden, aditivas, no destructivas, 30 migraciones = documentación).
- [x] Frontend correcto (guards, errores, traducciones, sin cálculo financiero duplicado).
- [x] Documentación de V2 (`SCOPE`, `PROGRESS`, `GAP`s) coherente con el estado real.
- [x] Diff limpio respecto al alcance (110 ficheros, todos `.java`/`.ts`/`.html`/`.sql`/`.md`; sin
      artefactos, sin secretos, sin cambios de config).
- [x] Sin cambios locales peligrosos (`git status` limpio).

Los 5 hallazgos menores son de coherencia documental (`CHANGELOG.md` / `12_DECISION_LOG.md` aún no
reflejan V2; nombres de migración en textos de planificación; verbo de R05) y su resolución natural
es el propio PR de merge. **Ninguno bloquea el merge.**

# `READY TO MERGE`

Con la recomendación de: (1) push + CI verde antes del merge, (2) añadir la sección de V2 a
`CHANGELOG.md` y `12_DECISION_LOG.md` en el PR de merge, (3) merge commit (no squash, no rebase),
(4) tag `v2.0.0` solo tras el merge con confirmación del propietario.
