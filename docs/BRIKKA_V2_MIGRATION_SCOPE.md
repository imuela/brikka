# BRIKKA V2 — ALCANCE CERRADO DE LA MIGRACIÓN FUNCIONAL (LEGACY → BRIKKA V2)

> **Estado: alcance APROBADO por el propietario del producto (decisiones definitivas registradas en
> §10).** La implementación (Fase 8) se limita **estrictamente a I1–I5**. No se añaden
> funcionalidades descubiertas durante la implementación: cualquier hueco o idea nueva se registra
> en `§6 FUTURO` y **no se implementa**.
>
> Documentos hermanos: `BRIKKA_V2_FUNCTIONAL_GAP.md` (Fase 2), `BRIKKA_V2_BUSINESS_RULES_GAP.md`
> (Fase 3). Seguimiento vivo: `BRIKKA_V2_MIGRATION_PROGRESS.md`.

---

## 0. Premisa

La Nueva Brikka ("V1", 40 sprints, release 1.0.0) **ya cubre la mayor parte del valor funcional de
la Legacy**, y en varios casos la mejora (perfil financiero con procedencia, versionado documental,
matching bancario, DTI real, cálculo de cuota por sistema francés, multi-tenant estructural, portal
cliente, IA de extracción documental). El equipo de V1 **ya usó esta auditoría** para incorporar el
modelo financiero del cliente y los honorarios.

Por tanto **V2 no es una migración**, es un **cierre de huecos acotado**: **5 bloques funcionales**
(I1–I5), todos derivados de una carencia real verificada en código. **I6 (contratos legales) queda
FUERA de V2** por decisión del propietario (no existe clausulado jurídico aprobado — ver §6).

---

## 1. INCLUIDO — funcionalidades que V2 incorporará (I1–I5)

> Cada ítem indica su origen (fila de `BRIKKA_V2_FUNCTIONAL_GAP.md` / regla de
> `BRIKKA_V2_BUSINESS_RULES_GAP.md`) y su prioridad.
>
> **Principio transversal de diseño (decisión §10.8):** todo lo que se construya en I1–I5
> (documentos, checklist, datos financieros, scoring, dossier) debe quedar diseñado de forma que
> **después pueda conectarse un `AiProvider` sin modificar el dominio**. En V2 **no se implementa
> ninguna IA real**; solo se dejan las costuras (interfaces, estados, campos de procedencia) listas.

### I1 · Checklist documental del expediente  *(P0 — GAP fila 12; reglas R13, R14)*

- **Semilla** de `document_requirements` para `operation_type = PURCHASE` con el mapa Legacy
  traducido a los códigos ya sembrados en `V2`:
  - por **titular**: `DNI`, `PAYSLIP`, `EMPLOYMENT_HISTORY` obligatorios; `INCOME_TAX_RETURN`,
    `EMPLOYMENT_CONTRACT`, `BANK_STATEMENT` opcionales.
  - del **expediente**: `LAND_REGISTRY_EXTRACT`, `DEPOSIT_CONTRACT` obligatorios; `PROPERTY_APPRAISAL`
    opcional.
- **Auto-generación** de `document_requests` del caso al entrar en `DOCUMENTATION` (idempotente),
  distinguiendo requisito **por titular** (`requested_from_client_id`) vs **del expediente**.
- **Regla de completitud (decisión §10.3):** un requisito **NO** se considera completo por el mero
  hecho de que exista un archivo. Debe existir **evidencia documental + estado de revisión/
  aprobación**. Ciclo:

  ```
  (sin documento) → SUBIDO → REVISADO/APROBADO → requisito COMPLETO
  ```

  El requisito solo pasa a completo cuando la `DocumentVersion` correspondiente alcanza
  `review_status = APPROVED` (reutilizando el flujo de revisión ya existente en `document`). Un
  documento subido pero no aprobado deja el requisito en estado **SUBIDO / pendiente de revisión**,
  visible como tal.
- **Diseño AI-ready (decisión §10.3, sin implementar IA ahora):** el modelo del checklist y del
  cierre de requisitos debe permitir que, en el futuro, una clasificación/extracción por IA
  proponga el `document_type` y el titular de un documento y lo enganche a un requisito `PENDING` —
  la pieza que hoy hace el humano (elegir tipo, subir, revisar) debe quedar como un punto de
  extensión, no cableada al formulario.
- **Endpoint + vista** en `case-detail`: **completitud del checklist** — pendientes obligatorios /
  opcionales, por titular y de expediente, con el estado de cada uno (`SIN_DOCUMENTO` / `SUBIDO` /
  `APROBADO`).
- Backend + frontend + tests (unit del servicio de auto-generación y del cierre por aprobación;
  test de aislamiento de tenant; componente Angular con test).

### I2 · Scoring "de fábrica" + indicador RAG del expediente  *(P1 — GAP filas 11c, 11d; reglas R10, R12)*

- Migración Flyway que **siembra un `scoring_ruleset` `ACTIVE`** (código `default-property-v1`) con:
  reglas sobre `LTV` y `REQUESTED_AMOUNT`/`TERM_MONTHS` (pesos y umbrales razonables, **editables vía
  el ruleset**, nunca hardcoded) y **3 categorías** (`SOLIDO` / `VIGILANCIA` / `BLOQUEADO`, con
  `maxScore` configurable + catch-all).
- **Indicador RAG del expediente** (decisión §10.1: **cualitativo y configurable vía ruleset, NO una
  copia de la fórmula Legacy; NO un segundo sistema de scoring paralelo**). Endpoint de solo lectura,
  sin persistencia nueva: combinación **cualitativa** del **peor** de los ejes disponibles:
  1. categoría del último `scoring_result` (inmueble/operación),
  2. categoría del último `case_financial_analysis_result` (viabilidad DTI, por cliente),
  3. completitud del checklist documental obligatorio (I1),
  4. (extensible: cualquier otro indicador cualitativo que Brikka ya exponga).

  Sin medias ponderadas, sin umbrales numéricos fuera del ruleset. Ejes ausentes → el indicador
  degrada a "sin evaluar", no a verde.
- Badge en `case-detail` y en `case-list`.
- Tests: resolución de categoría con el ruleset sembrado; combinación RAG con datos completos /
  parciales / ausentes; tenant.

### I3 · Precondiciones de transición del expediente  *(P1 — regla R08 revisada; spec `13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §5`)*

**Ampliado a tres transiciones (decisión §10.2):**

| Transición | Precondición | Fuente del dato |
|---|---|---|
| `DOCUMENTATION → ANALYSIS` | Checklist documental **obligatorio completo** (todos los requisitos obligatorios en estado `APROBADO`, I1) | I1 |
| `BANK_SEARCH → BANK_SUBMISSION` | Existe **al menos una solicitud/relación bancaria válida** para el caso (`bank_requests` con estado que represente "objetivo seleccionado") | `bankrequest` domain (ya existe) |
| `OFFER → FORMALIZATION` | Existe una **oferta seleccionada** (`final_financing` del caso → `bank_offer_id`) | `bankrequest` / `financing` (ya existe) |

- **Mecanismo de excepción autorizada** (se mantiene, decisión §10.2): un usuario con permiso
  específico puede forzar la transición aportando un **motivo obligatorio**, que queda en
  `case_status_history.reason`.
- **No** se implementa ningún gate por número de score (regla Legacy R08 numérica ≥60/≥70:
  `DESCARTADO`, §10.1).
- **No** se crean gates adicionales más allá de estos tres (decisión §10.2).
- Tests por transición: bloqueada / permitida / con excepción autorizada; mensaje de error
  estructurado (`{code, message, requestId}`).

### I4 · Simulación hipotecaria enriquecida  *(P1 — GAP filas 17, 17a, 17b; reglas R18, R19)*

- **`interest_type`** (`FIXED` / `VARIABLE` / `MIXED`) + desglose: `spread`, `euribor`,
  `fixed_years`, `fixed_rate`, `variable_spread` (columnas nuevas en `simulations` **o** `metadata`
  tipado — decisión de diseño dentro del sprint, sin `double`, dinero `numeric(14,2)`, tipos
  `numeric(7,4)`). Validación **"años fijos < plazo total"** para `MIXED`.
- **Bonificaciones**: catálogo cerrado (`SALARY`, `HOME_INSURANCE`, `LIFE_INSURANCE`, `ALARM`,
  `CARD`, `INVESTMENTS`, `OTHER`) con tasa por bonificación; `rate_final = max(0, rate_base − Σ tasas
  activas)`, **calculado en el backend** (NO reproducir el bug Legacy de guardarlas sin aplicarlas —
  regla R19).
- Recalcular `estimated_payment` con `MortgagePaymentCalculator` (sistema francés, ya existe) a
  partir del `rate_final`.
- Persistir `base_interest_rate`, bonificaciones aplicadas y `final_interest_rate`; mostrarlos en el
  diálogo de simulación y en el dossier (I5).
- Flag **`ico_guarantee`** a nivel de operación (`cases` u `properties` — decisión de diseño dentro
  del sprint) y reflejo en la simulación.
- Backend + `create-simulation-dialog` (Angular) + tests (cálculo del tipo final con varias
  bonificaciones, floor 0; cuota francesa; validación MIXED; redondeo monetario y de tipos).

### I5 · Dossier de viabilidad con documentación empaquetada + narrativa determinista  *(P1 — GAP filas 18, 18a, 21, 22; regla R12/R22-parcial)*

- Nueva acción **"Descargar toda la documentación del caso"**: **ZIP en streaming** con los
  `Document` del caso (última versión de cada uno), organizados por tipo / titular, servido con
  control de acceso `DOCUMENT_DOWNLOAD` y aislamiento de tenant por documento. **Sin ficheros
  temporales en disco** (mejora sobre la Legacy, que usaba `sys_get_temp_dir()` y además tenía un
  IDOR — S03 de la auditoría).
- El `ViabilityDossierService` incorpora un enlace/registro a la descarga combinada (dossier HTML +
  ZIP documental — decisión de diseño dentro del sprint).
- **Narrativa determinista (decisión §10.8: es funcionalidad del dossier, NO IA):** portar la
  lógica por reglas de `DossierNarrativeService` de la Legacy (nº de titulares, homogeneidad
  laboral, media de antigüedad e ingresos, comparación precio/valor del inmueble) como un
  `NarrativeService` Java, **sin dependencia de ningún proveedor de IA**. Diseñado de forma que un
  `AiProvider.explain` pueda sustituir/enriquecer el texto en el futuro sin tocar el dominio del
  dossier.
- Backend + botón en `case-detail` / `viability-dossier` + tests (contenido y estructura del ZIP;
  permisos y tenant; caso sin documentos; textos de narrativa por rama).

---

## 2. MEJORADO — funcionalidades Legacy que entran con diseño superior

| Legacy | En V2 se incorpora como | Mejora respecto a Legacy |
|---|---|---|
| Checklist en tabla `case_document_checklist` con flag manual | Catálogo `document_requirements` **condicional por tipo de operación** + `document_requests` con procedencia + **cierre por revisión/aprobación** | Condicional, catalogado, auditable, por titular; cierre por **evidencia aprobada**, no por presencia de fichero; AI-ready |
| Semáforo con umbrales `55/70` hardcoded en PHP | Categorías de `scoring_ruleset` **versionadas y editables** + **RAG cualitativo** del caso (peor eje) | Sin números mágicos en código; reproducible por versión de reglas; sin segundo sistema de scoring |
| `bonus_*` guardadas y **nunca aplicadas** (bug Legacy) | Bonificaciones con catálogo + cálculo `rate_final` en backend + cuota recalculada | La funcionalidad **funciona** (Legacy no); base/final visibles y trazables |
| Simulación con `interest_rate` plano | `interest_type` + desglose euríbor/diferencial/tramo mixto | Modela el producto hipotecario español real |
| ZIP a fichero temporal en `sys_get_temp_dir()`, sin comprobación de tenant | ZIP en **streaming**, con control de acceso y tenant **por documento** | Sin fichero temporal; cierra el IDOR de `reports/bank/download.php` (S03) |
| Narrativa por reglas en un servicio PHP acoplado | `NarrativeService` Java desacoplado, **AI-ready** | Preparado para que un `AiProvider` lo enriquezca sin tocar el dominio |

---

## 3. DESCARTADO — funcionalidades que deliberadamente NO se migran

| Elemento Legacy | Motivo |
|---|---|
| Estructura **física** de carpetas `01_IDENTIFICACION … 06_FACTURACION` | Sustituida por catálogo de tipos + versionado; carpetas físicas son deuda |
| **Replicación física** de documentos cliente→casos (`ClientDocumentsSyncService`) | Fuente de bugs; la relación lógica lo hace innecesario |
| Tabla `mortgage_holders` | Abandonada en la Legacy; `case_clients` la cubre |
| Endpoints rotos `reports/bank/generate.php`, `reports/client/generate_pdf.php` | Llaman a método inexistente |
| `index.php` duplicado, `calculateFinalInterest` duplicada, `CLIENT_SCORING` sin uso | Código muerto |
| **Score de cliente por puntos** (`calculateClientScore`) | **Decisión §10.1**: pesos/tramos arbitrarios sin fuente; NO mantener dos sistemas de scoring paralelos. La evaluación de cliente la cubre el **análisis DTI/viabilidad** ya existente |
| **Score combinado 0,65·cliente + 0,35·inmueble** (`calculateCaseScore`) | **Decisión §10.1**: ponderación sin base; sustituida por el **indicador RAG cualitativo** (I2) |
| **Gate de transición por número de score** (`scoringAllowsStatus`: ≥60, ≥70) | **Decisión §10.1**: umbrales inventados; sustituido por precondiciones cualitativas (I3) |
| **Honorarios con importes fijos** por tipo de inmueble (500/1.500/2.000/750/2.250) | Es la tarifa de **un** intermediario, no una regla de producto; `casefee` genérico ya lo cubre |
| Pack de identificación / **"Ficha del titular" PDF** (`CaseIdentificationService`) | El DNI ya es `Document`; la ficha se solapa con el dossier |
| `case_document_checklist.status` manual + `toggle_document.php` | Sin UI en la Legacy; el cierre por evidencia aprobada (I1/R14) es superior |
| Contraseña inicial `changeme123`, `display_errors=1`, credenciales de BD en claro | Resuelto por diseño en la Nueva |
| Regeneración+borrado automático de contratos en cada edición | Se conserva el versionado inmutable bajo demanda |
| Requisito documental que se cierra solo por "existe archivo" | **Decisión §10.3**: exige revisión/aprobación |

---

## 4. Modelo de datos y campos — comparación (Fase 4, actualizada con decisiones §10)

### 4.1 Campos Legacy que YA existen en la Nueva (sin trabajo)

| Ámbito | Campos Legacy | Dónde están en la Nueva |
|---|---|---|
| Cliente — personales | `full_name, dni, birth_date, nationality, address, employment_status` | `clients` (V18) |
| Cliente — financiero | `marital_status, dependents, employment_type, contract_type, company(empleador), years_employed, monthly_income, savings, other_loans, credit_cards` | `client_financial_profiles` (V22) **1:1** + `source/status/evidence` + historial |
| Expediente | `status, created_at, updated_at, broker_id, company_id` | `cases` + `case_assignments` + `case_status_history` |
| Expediente — importes | `purchase_price, property_value, loan_amount` | `properties.purchase_price/valuation` + `financing_requests.requested_amount/term_months` + `cases.requested_amount` (V19) |
| Inmueble | `property_type` | `properties.property_type` (texto) |
| Titulares | `case_clients.role='titular'` | `case_clients.participation_type` + `is_primary` |
| Simulación | `loan_amount, years, interest_rate, monthly_payment_*` | `simulations.principal, term_months, interest_rate, estimated_payment` |
| Simulación → oferta | `is_offer, bank_name, selected` | `bank_offers` + `final_financing` (separado) |
| Documentos | `document_type, original_name, uploaded_by, uploaded_at`, revisión | `documents` + `document_versions` (checksum, mime, size, `review_status`, `reviewed_by/at`) |
| Histórico de estados | `case_status_history` | `case_status_history` (+ `reason`, `metadata`) |
| Honorarios | (importes en `CaseContractsService`) | `case_fees` (V25) + `case_fee_history` |

### 4.2 Campos que V2 (I1–I5) debe añadir

| Campo | Propuesta de modelado (PostgreSQL / JPA) | Bloque | Prioridad |
|---|---|---|---|
| Semilla `document_requirements` (mapa `CLIENT_DOCUMENTS`/`CASE_DOCUMENTS` → códigos `V2`) | filas `INSERT` en migración Flyway nueva, `operation_type='PURCHASE'` | I1 | **P0** |
| Estado de completitud del requisito (`SIN_DOCUMENTO`/`SUBIDO`/`APROBADO`) | derivado en el servicio a partir de `document_requests.status` + `documents.review_status`; si hace falta, columna `document_requests.fulfilled_at` / `fulfilled_by_version_id` | I1 | **P0** |
| Semilla `scoring_ruleset` + `scoring_rules` + 3 categorías | filas `INSERT` en migración Flyway nueva | I2 | P1 |
| `simulations.interest_type` (`FIXED`/`VARIABLE`/`MIXED`) + `spread, euribor, fixed_years, fixed_rate, variable_spread` | columnas nuevas `numeric` / `varchar(20) CHECK(...)` **o** `metadata` tipado | I4 | P1 |
| `simulations.base_interest_rate`, `simulations.final_interest_rate` `numeric(7,4)` | columnas nuevas | I4 | P1 |
| Bonificaciones de simulación | tabla `simulation_bonifications(id, simulation_id, type CHECK(...), rate numeric(7,4))` **o** array tipado en `metadata` | I4 | P1 |
| `ico_guarantee` | `boolean NOT NULL DEFAULT false` en `cases` (o `properties`) | I4 | P2 (dentro de I4) |

### 4.3 Campos Legacy que NO se recuperan (confirmado)

`case_simulations.recommended`, `monthly_payment_phase2`, `total_interest` (siempre 0 en Legacy) ·
`clients.legal_type` (no se persiste en Legacy) · `case_document_checklist.status` (flag manual) ·
tabla `mortgage_holders` · convención de rutas físicas · **`companies.legal_type` / representante
legal / `logo`** (dependían de I6 → FUTURO, §10.6) · **`properties.condition` / `location_tier`**
(no forman parte de I1–I5 → FUTURO).

### 4.4 Principio de modelado

No se copia el esquema MySQL. Dinero `numeric(14,2)`, tipos de interés `numeric(7,4)`, `timestamptz`,
`uuid` PK, FK a `companies(id)` para tenant. `CHECK` para catálogos cerrados propios de Brikka
(`interest_type`, tipos de bonificación, categorías de ruleset). Historial append-only donde el dato
sea "de negocio controlado" (patrón `client_financial_profile_history` / `case_fee_history`).
Interfaces y estados diseñados para que un `AiProvider` se enganche después **sin tocar el dominio**.

---

## 5. IA — posición en V2 (Fase 7, actualizada con decisión §10.8)

- **La narrativa determinista del dossier NO cuenta como IA.** Es funcionalidad del dossier (I5) y
  entra en V2. Se implementa como reglas Java (`NarrativeService`), sin proveedor.
- **Ninguna IA real entra en V2.** Quedan FUERA (FUTURO, §6): OCR, clasificación automática de
  documentos, extracción IA → perfil financiero, detección de inconsistencias por IA, resumen de
  expediente por IA, asistente/consultas por IA, borradores de comunicaciones por IA.
- **Requisito de diseño (obligatorio en I1–I5):** documentos, checklist, datos financieros, scoring y
  dossier deben quedar diseñados para **conectar un `AiProvider` después sin modificar el dominio**.
  La abstracción ya existe (`ai.AiProvider` síncrono; `ai.AiTaskDispatcher` → `ai-worker` con
  `AI_PROVIDER` = `anthropic` | `ollama` local | `none`; `ai_usage`; gating en `AiUseCaseService`).
  V2 no la amplía; solo deja los puntos de extensión (p. ej.: el enganche "documento → requisito"
  de I1 como interfaz, no cableado al formulario; el texto de narrativa de I5 como estrategia
  sustituible).

---

## 6. FUTURO — NO forma parte de esta migración

Registrado y documentado; retomar solo con decisión y prioridad explícitas del propietario.

### 6.1 Contratos legales (ex-I6) — decisión §10.4
- **Información Previa y Honorarios**, **Contrato de Intermediación LCCI (Ley 5/2019)**,
  **Autorización RGPD**, como documentos parametrizados + versionados.
- **Bloqueado por:** no existe clausulado jurídico aprobado. No se genera ni se inventa texto legal.
- **Dependencias que también quedan FUTURO por esto (decisión §10.6):** `companies.legal_type`
  (autónomo/jurídica), campos de representante legal / identificación / nº de registro, **logo de
  empresa**. No se añaden a `Company` solo para soportar contratos.
- Retomar cuando exista clausulado aprobado por la empresa.

### 6.2 Render a PDF real — decisión §10.5
- Generación de PDF (Gotenberg / OpenPDF / Flying Saucer) de dossier, informes y (futuros)
  contratos. El **HTML versionado actual es suficiente para V2**.

### 6.3 Honorarios — decisión §10.7
- **Tarifas configurables por empresa** (p. ej. `habitual` → fijo + %; `autónomo` → otro importe).
- Motor de devengo/facturación (reglas temporales R23: no reembolsable tras inicio, éxito exigible
  a 3 meses del desistimiento, prórroga trimestral, duración 12 meses).
- El modelo `casefee` actual (`FIXED`/`PERCENTAGE`, `PROPOSED/AGREED/CANCELLED`, historial) es
  suficiente para V2.

### 6.4 IA real — decisión §10.8
- Extracción IA → borrador de `ClientFinancialProfile` (`source='AI'`, `status='PENDING'`, revisión
  humana).
- Clasificación automática de documentos y casado con el checklist (I1) — I1 se diseña AI-ready para
  esto.
- Resumen de expediente / explicación verbalizada del scoring (I2) / borradores de comunicaciones
  (requieren aprobar un `AiProvider` real).
- Detección de inconsistencias documento↔dato declarado.
- Asistente / consultas sobre el expediente.

### 6.5 Otros
- Score combinado único del expediente / gate de transición por score numérico.
- Precondiciones de transición adicionales a las tres de I3 (solo si son imprescindibles para la
  coherencia del flujo — hoy no se ven).
- Campos de inmueble: condición de conservación (`nuevo/buen_estado/a_reformar`), tipología de
  ubicación (`prime/urbana/rural`); catálogo cerrado de tipo de inmueble.
- Seed de ~45 entidades bancarias españolas + `bank_criteria_versions` por banco.
- Informe de viabilidad para el cliente como documento independiente (el dossier ya cubre el
  contenido analítico).
- Tope de asientos por empresa como entitlement de plan.
- Unicidad parcial de `document_number` de cliente por empresa (regla R05) — mejora de integridad,
  no hueco funcional Legacy.
- **(V2-2)** `case-detail`: recargar automáticamente la sección "Checklist documental" al cambiar
  el estado del caso (hoy solo se recarga tras acciones documentales — enganche de I1). Cosmético.

**Regla de control de alcance:** si durante la implementación de I1–I5 aparece una funcionalidad
nueva interesante, se anota aquí y **no se implementa**.

---

## 7. FIN DE MIGRACIÓN — condición de cierre

La migración funcional Legacy → Brikka V2 está **TERMINADA** cuando, para **cada bloque I1–I5**:

1. Existe implementación **backend** (servicio + endpoint + migración Flyway si aplica), integrada
   con seguridad (permiso) y multi-tenant.
2. Existe implementación **frontend** donde aplica, integrada con guards y manejo de errores
   existentes.
3. Existen **validaciones** de entrada con el contrato de error estándar
   (`{code, message, requestId}`, 400 estructurado).
4. Existen **tests backend** (servicio + un test de aislamiento de tenant por recurso nuevo) y
   **tests frontend** donde hay componente nuevo.
5. La **batería completa** (backend `mvn test` + frontend) pasa en verde.
6. La documentación afectada (`docs/`) está actualizada y `BRIKKA_V2_MIGRATION_PROGRESS.md` marca el
   bloque como completado.
7. El diseño de cada bloque deja los puntos de extensión para un futuro `AiProvider` **sin acoplar
   el dominio** (§5).

Cuando se cumpla para **I1, I2, I3, I4 e I5**, se declara explícitamente:

> **MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA**

y **se detiene el trabajo de migración**. Cualquier hueco descubierto después → `§6 FUTURO`.

---

## 8. Sprints — orden definitivo (Fase 8: NO ejecutar hasta confirmar §10.10)

> El número de sprints **deriva del alcance**: **6 sprints** (V2-0 de preparación + 5 de bloque).
> El orden respeta las dependencias reales: I2 e I3 consumen la completitud del checklist de I1.

| Sprint | Bloque | Depende de | Objetivo | Archivos afectados (indicativo) | Tests | Criterios de aceptación |
|---|---|---|---|---|---|---|
| **V2-0** · Preparación | — | — | Confirmar/commitear o *stashear* los 4 archivos locales de `client-form` (Fase 0); crear `BRIKKA_V2_MIGRATION_PROGRESS.md`; congelar este alcance | `BRIKKA_V2_MIGRATION_PROGRESS.md` (nuevo) | — | working tree limpio; progreso inicializado; alcance §1–§7 sin contradicciones |
| **V2-1** · Checklist documental (I1, **P0**) | V2-0 | seed `document_requirements(PURCHASE)`; auto-gen de `document_requests` al entrar en `DOCUMENTATION`; cierre de requisito solo con `review_status = APPROVED`; endpoint + vista de completitud; diseño AI-ready del enganche "documento→requisito" | migración `V27__seed_document_requirements.sql`; `document/DocumentRequestService.java`, `document/DocumentService.java` (hook al aprobar), `casemgmt/CaseService.java` (hook de transición); nuevo `document/CaseChecklistService.java` + controller; `frontend/.../cases/case-detail/*` + nuevo componente de checklist + `documents.service.ts` | auto-gen idempotente; requisito NO se cierra al subir, SÍ al aprobar; tenant; componente Angular | al pasar `PURCHASE` a `DOCUMENTATION` aparecen los requisitos por titular y de expediente; subir el documento deja el requisito en `SUBIDO`; aprobarlo lo deja `APROBADO`; la vista muestra "faltan N obligatorios" |
| **V2-2** · Precondiciones de transición (I3, P1) | V2-1 (para la primera transición) | 3 gates: `DOCUMENTATION→ANALYSIS` (checklist obligatorio aprobado), `BANK_SEARCH→BANK_SUBMISSION` (≥1 solicitud bancaria válida), `OFFER→FORMALIZATION` (oferta seleccionada / `final_financing`); excepción autorizada con motivo | `casemgmt/CaseService.java` (`changeStatus`), sin cambios en `CaseWorkflow`; permiso nuevo `CASE_TRANSITION_OVERRIDE` (o equivalente); `frontend/.../cases/case-dialogs/change-status-dialog.component.ts` | por transición: bloqueada / permitida / con excepción; error estructurado | no se avanza si la precondición falla; con permiso + motivo sí, y queda en `case_status_history.reason` |
| **V2-3** · Scoring de fábrica + RAG (I2, P1) | V2-1 (eje "checklist" del RAG) | migración seed `scoring_ruleset` `ACTIVE` + 3 categorías; endpoint RAG del caso (peor eje, cualitativo); badge en `case-detail` y `case-list` | migración `V28__seed_default_scoring_ruleset.sql`; nuevo `scoring/CaseIndicatorService.java` + controller; `frontend` `scoring.service.ts` + `case-detail`/`case-list` + `shared/status-badge` | resolución de categoría con el ruleset sembrado; combinación RAG completa/parcial/ausente; tenant | caso con LTV bajo + viabilidad FAVORABLE + checklist completo → verde; cualquier eje en rojo → rojo; sin datos → "sin evaluar" |
| **V2-4** · Simulación enriquecida (I4, P1) | V2-0 | `interest_type` + desglose; bonificaciones (catálogo + tasa); `rate_final` en backend; cuota recalculada con `MortgagePaymentCalculator`; flag `ico_guarantee` | migración `V29__simulation_interest_type_and_bonifications.sql`; `financing/Simulation.java`, `SimulationRepository`, `SimulationController`, `CreateSimulationApiRequest`; `frontend` `create-simulation-dialog.component.ts` | `rate_final` con varias bonificaciones (floor 0); cuota francesa; validación MIXED (años fijos < plazo); redondeo `numeric(7,4)`/`numeric(14,2)` | una simulación VARIABLE con euríbor + diferencial + 2 bonificaciones muestra base, final y cuota coherentes; el dossier lo refleja |
| **V2-5** · Dossier + ZIP documental + narrativa determinista (I5, P1) | V2-1 (documentos), V2-4 (simulación en el dossier) | endpoint "descargar toda la documentación del caso" (ZIP streaming, permisos, tenant); dossier combina/enlaza; `NarrativeService` determinista (reglas), AI-ready | nuevo `document/CaseDocumentsArchiveService.java` + controller; `dossier/ViabilityDossierService.java` (narrativa + enlace ZIP); nuevo `dossier/NarrativeService.java`; `frontend` `viability-dossier.service.ts` + botón en `case-detail` | contenido y estructura del ZIP; permisos y tenant; caso sin documentos; narrativa por rama (1 titular / varios; antigüedad; ingresos) | el broker descarga un ZIP con todos los documentos del caso; el dossier incluye un párrafo de contexto generado por reglas |

### Cierre (dentro de V2-5)
Batería completa backend + frontend en verde · `BRIKKA_V2_MIGRATION_PROGRESS.md` al 100 % de I1–I5 ·
actualización de `docs/12_DECISION_LOG.md` y `CHANGELOG.md` · declaración **MIGRACIÓN LEGACY →
BRIKKA V2 COMPLETADA**.

### Número total de sprints
**6** — `V2-0`, `V2-1`, `V2-2`, `V2-3`, `V2-4`, `V2-5`.

---

## 9. Criterio de finalización (resumen ejecutable)

`MIGRACIÓN COMPLETADA` ⇔ para **I1, I2, I3, I4, I5**: backend + frontend (si aplica) + validaciones +
tests backend + tests frontend (si aplica) + integración seguridad/tenant + diseño AI-ready +
batería completa en verde + progreso al 100 %. Alcanzado eso → declararlo y **parar**. Todo lo demás
vive en `§6 FUTURO`.

---

## 10. Decisiones del propietario del producto (definitivas)

1. **Scoring de cliente Legacy → DESCARTAR** (puntos de cliente + ponderación 65/35). No mantener dos
   sistemas de scoring paralelos. La evaluación moderna usa viabilidad/DTI + scoring de
   operación/inmueble + documentación + demás indicadores disponibles. El indicador RAG es
   **cualitativo y configurable vía ruleset**, no una copia de la fórmula Legacy.
2. **Precondiciones de transición → I3 ampliado a 3 transiciones**: `DOCUMENTATION→ANALYSIS`
   (documentación obligatoria completa), `BANK_SEARCH→BANK_SUBMISSION` (≥1 solicitud/relación
   bancaria válida), `OFFER→FORMALIZATION` (oferta seleccionada). Se mantiene la excepción
   autorizada. No crear gates adicionales salvo imprescindibles para la coherencia del flujo.
3. **Checklist documental → cierre por evidencia + revisión/aprobación**: `SUBIDO →
   REVISADO/APROBADO → requisito completo`. No basta con que exista archivo. Diseñar AI-ready
   (clasificación/extracción por IA integrable después). No implementar IA documental ahora.
4. **Contratos legales (I6) → FUERA de V2 (FUTURO)**. No generar ni inventar clausulado jurídico.
   Documentada la necesidad futura de Información Previa y Honorarios, Contrato de Intermediación
   LCCI y Autorización RGPD. Se retoma cuando exista clausulado aprobado.
5. **PDF → FUTURO**. El HTML versionado actual es suficiente para V2.
6. **Datos legales de `Company` (representante legal, tipo jurídico, logo) → FUTURO**, junto con los
   contratos. No añadir solo para soportar I6.
7. **Honorarios → sin cambios en V2**. Tarifas configurables por empresa → FUTURO.
8. **IA → fuera de V2**. La narrativa determinista NO es IA y sí forma parte de V2 (dossier). Todo el
   diseño de documentos, datos financieros, scoring y dossier debe permitir conectar un `AiProvider`
   después **sin modificar el dominio**. No implementar ahora: OCR, clasificación automática,
   extracción IA, detección de inconsistencias por IA, resumen IA, asistente IA.
9. **Alcance final V2 = I1, I2, I3, I4, I5.** I6 → FUTURO. La migración termina cuando I1–I5 estén
   completamente implementados y validados.
10. **Antes de programar**: actualizar `BRIKKA_V2_MIGRATION_SCOPE.md`, `BRIKKA_V2_FUNCTIONAL_GAP.md`,
    `BRIKKA_V2_BUSINESS_RULES_GAP.md`; crear `BRIKKA_V2_MIGRATION_PROGRESS.md`; presentar alcance
    final y orden de sprints; confirmar ausencia de contradicciones. La implementación de I1–I5 no
    empieza hasta ese punto. Después, la implementación se limita **estrictamente a I1–I5**;
    cualquier idea nueva → FUTURO.
