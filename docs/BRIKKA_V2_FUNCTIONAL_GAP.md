# BRIKKA V2 — MATRIZ DE GAP FUNCIONAL (LEGACY → NUEVA BRIKKA)

> **Actualizado con las decisiones definitivas del propietario** (ver
> `BRIKKA_V2_MIGRATION_SCOPE.md §10`). Cambios respecto a la versión de análisis inicial:
> - **Alcance de implementación V2 = I1–I5.** I6 (contratos legales) → **FUTURO**.
> - Scoring de cliente por puntos + ponderación 65/35 + gate numérico de transición → **DESCARTADO**
>   (confirmado). El indicador de caso es un **RAG cualitativo configurable vía ruleset**.
> - I3 (precondiciones de transición) → **3 transiciones** (DOCUMENTATION→ANALYSIS,
>   BANK_SEARCH→BANK_SUBMISSION, OFFER→FORMALIZATION).
> - Checklist (I1): un requisito se cierra con **revisión/aprobación**, no con "existe archivo".
> - PDF real, datos legales de `Company` (representante/logo/tipo jurídico), tarifas por empresa,
>   toda la IA real, campos de inmueble (condición/ubicación), seed de bancos → **FUTURO**.
>
> ---
>
> Este documento compara funcionalmente
> `/Users/Isra/Desarrollo/imgsbroker/brikka` (Legacy, PHP/MySQL) con `/Users/Isra/Desarrollo/brikka`
> (Nueva Brikka, Spring Boot + Angular + PostgreSQL, "Brikka V1" cerrada tras 40 sprints).
>
> Fuentes: `docs/LEGACY_FORENSIC_AUDIT.md` (auditoría previa), código Legacy, código backend
> (`backend/src/main/java/com/brika/platform/**`, 432 clases), migraciones Flyway (V1–V26),
> frontend Angular (`frontend/src/app/features/**`), specs de `docs/`.
>
> **Hallazgo transversal:** la Nueva Brikka **ya incorporó explícitamente** buena parte del valor de
> la Legacy usando esta misma auditoría (ver comentarios de `V22__client_financial_profile.sql`,
> `V25__case_fee.sql`, `ViabilityClassifier.java`, `FinancialAnalysisService.java`: *"Fields taken
> 1:1 from the Brikka Legacy field analysis"*). El gap real es **pequeño y acotado**.

Estados permitidos: `EXISTE` · `PARCIAL` · `FALTA` · `DIFERENTE` · `OBSOLETO` · `ROTO` · `DESCARTAR` · `REQUIERE DECISIÓN`
Acciones permitidas: `MANTENER` · `MIGRAR` · `MEJORAR` · `REDISEÑAR` · `DESCARTAR` · `REQUIERE DECISIÓN`

> **Nota de lectura:** la columna **Acción** de la matriz (§1) conserva el **análisis inicial**. La
> **disposición final** —tras las decisiones definitivas del propietario (`BRIKKA_V2_MIGRATION_SCOPE.md
> §10`)— está en **§7 y §8** de este documento. Donde en la matriz se lee `REQUIERE DECISIÓN`, la
> decisión **ya está tomada**: consúltese §8. Resumen: filas 14/14a/14b (contratos), 4a/4c (datos
> legales de empresa), 30 (PDF), 15 (tarifas), 19 (informe cliente PDF), 24/25 (campos de inmueble),
> 10b/11a/11b (scoring de cliente / score combinado / gate numérico) → **DESCARTADO para V2 o
> FUTURO**. Fila 10a (precondiciones) → **I3, ampliada a 3 transiciones**.

---

## 1. Resumen de la matriz  *(análisis inicial — disposición final en §7/§8)*

| # | Dominio / Funcionalidad Legacy | Legacy | Nueva Brikka | Estado | Acción | Prioridad |
|---|---|---|---|---|---|---|
| 1 | Autenticación (login, logout, sesión) | completo (bcrypt, sesión PHP) | completo (JWT RS256 autoemitido, Argon2id, refresh rotativo, reset de contraseña) | EXISTE | MANTENER | — |
| 2 | Enrutado / navegación por rol | 3 roles (superadmin/manager/broker) | RBAC 4 roles (SUPERADMIN/MANAGER/BROKER/CLIENT) + guards Angular + 110 permisos | EXISTE | MANTENER | — |
| 3 | Dashboards por rol (contadores) | 3 vistas de conteo | `dashboard` domain + `shell/dashboard` + overhaul superadmin/manager (commit `64c84a6`) | EXISTE | MANTENER | — |
| 4 | Empresas — CRUD | completo | `companies` CRUD + `company.service` + detail/form/list | EXISTE | MANTENER | — |
| 4a | Empresa — tipo jurídico (autónomo vs persona jurídica) | `legal_type` + `representative_name/dni` vs `legal_name/cif` | `Company(legalName, tradeName, taxId, status)` — sin distinción autónomo/jurídica ni representante | FALTA | REQUIERE DECISIÓN | P2 |
| 4b | Empresa — límite de usuarios | `user_limit` (3/10/∞) validado en alta de usuario | Modelo de **planes + entitlements + suscripciones** (`plan` domain, 17 clases) | DIFERENTE | MANTENER (moderno) | — |
| 4c | Empresa — logo (subida de imagen) | `uploadCompanyLogo()` → `uploads/logos/` | Sin campo `logo` en `Company` ni endpoint | FALTA | REQUIERE DECISIÓN | P2 |
| 4d | Empresa — borrado en cascada | `companies/edit.php` borra usuarios/casos/documentos/`mortgage_holders` | No verificado un borrado destructivo equivalente; el modelo usa `status` | DIFERENTE | MANTENER (moderno, no replicar borrado físico) | — |
| 5 | Usuarios — CRUD + activar/desactivar | completo; contraseña inicial fija `changeme123` | `identity` + `auth` (provisioning, credential bootstrap, reset); sin contraseña por defecto insegura | EXISTE | MANTENER | — |
| 6 | Clientes — CRUD | completo (~18 campos) | `crm.Client` + atributos extendidos (V18) + `clients` feature (list/detail/form) | EXISTE | MANTENER | — |
| 7 | Cliente — perfil financiero (ingresos, ahorro, deudas, antigüedad, empleo, situación familiar) | inline en `clients` (`monthly_income, savings, other_loans, credit_cards, years_employed, employment_type, contract_type, marital_status, dependents, company`) | `client_financial_profiles` (V22) **1:1 con Legacy** + `source/status/evidence` (procedencia) + historial append-only + `financial-profile` dialog | EXISTE (mejorado) | MANTENER | — |
| 8 | Documentación del cliente (6 tipos) + subida | `client_documents` + copia física a carpetas de casos | `document` domain (36 clases): tipos catalogados (V2), versionado inmutable, MinIO, URL presignada, revisión, publicación a portal | EXISTE (mejorado) | MANTENER | — |
| 8a | Replicación física de documentos cliente→casos | `ClientDocumentsSyncService` copia ficheros | No existe (relación lógica, no copia) | DIFERENTE | DESCARTAR (era fuente de bugs) | — |
| 9 | Expedientes ("casos") — CRUD, añadir titular, borrar | completo | `casemgmt` (27 clases): `Case`, `CaseClient` (HOLDER/CO_HOLDER/GUARANTOR/OTHER + `is_primary`), `CaseAssignment`, `case-detail`/`case-form`/`case-list` + diálogos | EXISTE (mejorado) | MANTENER | — |
| 10 | Máquina de estados del expediente | 6 estados (`nuevo…cerrado`/`rechazado`) | 10 estados (`13_DEFINITIVE_WORKFLOW_SPECIFICATION.md`), `CaseWorkflow` puro + `case_status_history` + eventos + cancelación con catálogo de motivos | EXISTE (mejorado) | MANTENER | — |
| 10a | Precondiciones de transición (documentación mínima, análisis suficiente, oferta seleccionada…) | checklist obligatorio completo para avanzar; gates por scoring | Solo **1 precondición** implementada: "≥1 cliente antes de DOCUMENTATION". El resto de precondiciones del spec §5 están **documentadas pero no implementadas** | PARCIAL | REQUIERE DECISIÓN | P1 |
| 10b | Gate de transición por scoring numérico (score ≥ 60 → ofertado; ≥ 70 → cerrado) | `scoringAllowsStatus()` | No existe | FALTA | REQUIERE DECISIÓN (umbrales Legacy sin base documentada) | P2 |
| 11 | Scoring — dimensión de INMUEBLE / OPERACIÓN (LTV, importe, plazo, valor, precio) | `calculatePropertyScore()` (LTV escalonado) | `scoring` engine (28 clases): rulesets/versiones configurables, 5 campos (`TERM_MONTHS, REQUESTED_AMOUNT, VALUATION, PURCHASE_PRICE, LTV`), resultados append-only con `explanation` | EXISTE (mejorado) | MANTENER | — |
| 11a | Scoring — dimensión de CLIENTE (ingresos, ahorro, antigüedad, penalización por deudas) | `calculateClientScore()` (puntos por tramos) | El motor de scoring **no puede referenciar** campos del perfil financiero. Existe un análisis **DTI** paralelo (ver fila 19) | FALTA | REQUIERE DECISIÓN | P1 |
| 11b | Scoring — score combinado del expediente (0,65·cliente + 0,35·inmueble) | `calculateCaseScore()` | No existe (ponderación Legacy sin base documentada) | FALTA | REQUIERE DECISIÓN | P2 |
| 11c | Semáforo del expediente (rojo/ámbar/verde) | `caseSemaphore()` (score + checklist completo) | Concepto de `category` configurable por ruleset (`ScoringEngine.resolveCategory`), pero **sin ruleset ni categorías sembradas** y sin indicador a nivel de caso | PARCIAL | MEJORAR | P1 |
| 11d | Ruleset de scoring por defecto (para que el scoring funcione "de fábrica") | pesos en constantes PHP | `run()` lanza `NO_ACTIVE_SCORING_RULESET` hasta que alguien crea uno vía API; **ninguna migración lo siembra** | FALTA | MIGRAR (seed) | P1 |
| 12 | Checklist documental (obligatorios/opcionales por titular + del expediente) | auto-generado al crear el caso (`case_document_checklist`); alimenta semáforo y gates | `document_requirements` (V5, catálogo condicional por `operation_type`) + `document_requests` con `requirement_id`; **sin datos sembrados**, **sin auto-generación al crear caso**, **sin vista de completitud agregada** | PARCIAL | MEJORAR | P0 |
| 13 | Estructura física de carpetas `01_IDENTIFICACION … 06_FACTURACION` | `createCaseFolders()` | Modelo por tipo de documento + versionado, sin carpetas | DIFERENTE | DESCARTAR (taxonomía de carpetas) | — |
| 14 | Contratos legales PDF: Información Previa y Honorarios | `reports/contracts/info_previa.php` (texto real) | No existe como documento propio | FALTA | REQUIERE DECISIÓN | P1 |
| 14a | Contrato de Intermediación (clausulado completo Ley 5/2019 / LCCI) | `reports/contracts/contrato_intermediacion.php` (16 cláusulas + anexos, datos del intermediario) | `contract.EngagementContractService` genera **1 HTML esquemático** explícitamente marcado como "plantilla técnica/provisional, no válida jurídicamente" | PARCIAL | MEJORAR | P1 |
| 14b | Autorización RGPD | `reports/contracts/rgpd.php` (texto real) | No existe como documento propio | FALTA | REQUIERE DECISIÓN | P1 |
| 15 | Honorarios — cálculo | reglas automáticas por tipo de inmueble + autónomo (500/1.500, 500/2.000, 750/2.250) | `casefee` (9 clases): `FIXED` / `PERCENTAGE`, `calculated_amount` determinista, estados `PROPOSED/AGREED/CANCELLED`, historial | DIFERENTE | REQUIERE DECISIÓN (¿portar sugerencia automática?) | P2 |
| 16 | Pack de identificación (copia DNI + "Ficha del titular" PDF) | `CaseIdentificationService` | No existe. El DNI ya es un `Document`; la "ficha" se solapa con el dossier | FALTA | DESCARTAR | — |
| 17 | Simulaciones — tipos de interés `fijo`/`variable`/`mixto` + euríbor/diferencial | `cases/simulations/*` | `financing.Simulation(principal, interestRate, termMonths, estimatedPayment, metadata)` — sin tipado de interés ni desglose | PARCIAL | MEJORAR | P1 |
| 17a | Simulaciones — bonificaciones que reducen el tipo (nómina, seguros, alarma, tarjeta, inversiones, otras) | columnas `bonus_*` (Legacy **las guarda pero no las aplica** — bug) | No existe | FALTA | MIGRAR (bien hecho: aplicando la bonificación) | P1 |
| 17b | Simulaciones — aval ICO | flag `ico_guarantee` en caso y simulación | No existe en ninguna parte | FALTA | MIGRAR | P2 |
| 17c | Simulación vs oferta bancaria (`is_offer`), oferta "seleccionada"/"recomendada" | mezclado en `case_simulations` | Separado limpiamente: `Simulation` ≠ `BankOffer` ≠ `FinalFinancing` (`bankrequest` domain) | DIFERENTE | MANTENER (moderno) | — |
| 17d | Cálculo de cuota / amortización | **no lo calcula** (`monthly_payment_phase1 = 0`) | `MortgagePaymentCalculator` (sistema francés real, BigDecimal, single source of truth) | EXISTE (mejorado) | MANTENER | — |
| 17e | Selector de ~45 entidades españolas (bancos + cajas rurales) | `<option>` estáticos | `banks` catálogo gestionado por API; seed mínimo dev (`DevSeedRunner.ensureBanks`) | PARCIAL | MIGRAR (seed) | P2 |
| 18 | Dossier bancario (PDF narrativo + ZIP con toda la documentación de titulares y expediente) | `reports/bank/download.php` + `CaseDocumentsZipService` | `dossier.ViabilityDossierService`: **1 HTML consolidado** (snapshot versionado) con clientes, perfil, análisis financiero, simulaciones, matching, ofertas, honorarios, estado documental | PARCIAL | MEJORAR | P1 |
| 18a | Empaquetado ZIP de todos los documentos del caso | `CaseDocumentsZipService`, `documents_zip.php`, `download_client_docs.php`, `download_legal_docs.php` | No existe ninguna descarga "todos los documentos del caso en ZIP" | FALTA | MIGRAR | P1 |
| 19 | Informe de viabilidad para el cliente (PDF con declaración de independencia LCCI) | `reports/client/download.php` (párrafos fijos) | `financialanalysis` (7 clases): **DTI real** por cliente, categoría `FAVORABLE/REVISAR/NO_VIABLE`, `rules_version`, disclaimer, append-only | EXISTE (mejorado) pero DIFERENTE | REQUIERE DECISIÓN (¿informe cliente independiente en PDF?) | P2 |
| 20 | Explorador de documentos + ver/descargar individual | `cases/documents.php`, `document_view.php`, `document_download.php` | `documents` feature (diálogo de versiones, descarga por URL presignada) | EXISTE (DIFERENTE) | MANTENER | — |
| 21 | Descargas agrupadas por bloque (legal / titulares) en ZIP | `download_legal_docs.php`, `download_client_docs.php` | No existe | FALTA | Cubierto por fila 18a | P1 |
| 22 | Generador de narrativa (prosa por reglas para el dossier) | `DossierNarrativeService` | `AiProvider.summarize/explain` = **`NoOpAiProvider`** (ningún proveedor aprobado); el dossier lleva texto plantillado mínimo | PARCIAL | MEJORAR | P2 |
| 23 | Mensajes flash | `setFlash/getFlash` | Snackbars + `friendlyErrorMessage` + contrato de error estándar de la API | EXISTE | MANTENER | — |
| 24 | Inmueble — condición (`nuevo`/`buen_estado`/`a_reformar`) y ubicación (`prime`/`urbana`/`rural`) | columnas en `cases` | `property.Property(address, propertyType, valuation, purchasePrice)` — sin condición ni tipología de ubicación | FALTA | REQUIERE DECISIÓN | P2 |
| 25 | Inmueble — tipo (`habitual`/`segunda_residencia`/`inversion`) | columna `property_type` en `cases` | `Property.propertyType` (texto libre) — se puede mapear al catálogo | PARCIAL | MEJORAR (catálogo cerrado) | P2 |
| 26 | Importes de la operación (precio compra, valor, importe préstamo) | inline en `cases` | Repartidos: `Property.valuation/purchasePrice` + `FinancingRequest.requestedAmount/termMonths` + `Case.requestedAmount/description` (V19) | EXISTE (mejorado) | MANTENER | — |
| 27 | `mortgage_holders` (tabla Legacy) | abandonada (solo se borra) | — | OBSOLETO | DESCARTAR | — |
| 28 | Endpoints Legacy rotos (`reports/bank/generate.php`, `reports/client/generate_pdf.php`) | llaman a método inexistente | — | ROTO | DESCARTAR | — |
| 29 | `index.php` ≟ `dashboard.php`, `calculateFinalInterest` duplicada, `CLIENT_SCORING` sin uso | duplicidad/código muerto Legacy | — | OBSOLETO | DESCARTAR | — |
| 30 | Salida en **PDF real** de contratos e informes | dompdf → PDF | `contract` y `dossier` generan **HTML** (como `DocumentVersion`) | DIFERENTE | REQUIERE DECISIÓN (¿render a PDF?) | P2 |

---

## 2. Funcionalidades presentes en AMBAS (paridad — no requieren trabajo)

Autenticación · roles y permisos · dashboards · CRUD de empresas · CRUD de usuarios + activación ·
CRUD de clientes · perfil financiero del cliente (Nueva ⊃ Legacy) · subida y gestión documental
(Nueva ⊃ Legacy) · CRUD de expedientes · vinculación de titulares a expediente · máquina de estados
del expediente + historial · scoring de inmueble/operación (Nueva ⊃ Legacy) · cálculo de cuota
(solo Nueva, real) · honorarios de caso (modelos distintos) · dossier de caso (formatos distintos) ·
análisis de viabilidad (Nueva ⊃ Legacy, DTI) · explorador/descarga de documentos · mensajería de
errores al usuario.

## 3. Funcionalidades Legacy AUSENTES en la Nueva Brikka (`FALTA`)

1. **Semilla de checklist documental** por tipo de operación + **auto-generación** de `document_requests` al crear/abrir el caso + **vista de completitud** (fila 12). — *el mecanismo existe, faltan datos y el enganche.*
2. **Ruleset de scoring por defecto** sembrado, con **categorías tipo semáforo** (filas 11c, 11d).
3. **Dimensión de cliente en el scoring** o decisión formal de que el análisis DTI la sustituye (fila 11a).
4. **Precondiciones de transición** más allá de "≥1 cliente" (fila 10a) — el spec ya las lista.
5. **Contratos legales completos**: Información Previa y Honorarios, Contrato de Intermediación LCCI (clausulado real), Autorización RGPD (filas 14, 14a, 14b).
6. **Simulación con tipo de interés (`FIXED/VARIABLE/MIXED`)**, desglose euríbor+diferencial, **bonificaciones que reducen el tipo**, **flag ICO** (filas 17, 17a, 17b).
7. **Empaquetado ZIP de todos los documentos del caso** (fila 18a) — y como parte del dossier bancario.
8. **Campos de inmueble**: condición, tipología de ubicación; catálogo cerrado de tipo de inmueble (filas 24, 25).
9. **Seed de entidades bancarias españolas** (~45) (fila 17e).
10. **Logo de empresa** y **datos del representante legal** (necesarios para los contratos) (filas 4a, 4c).

## 4. Funcionalidades PARCIALES en la Nueva Brikka (`PARCIAL`)

- Checklist documental (mecanismo sí, seed + auto-generación + vista no) — fila 12.
- Semáforo / categorías de scoring (concepto sí, seed no) — fila 11c.
- Contrato de encargo (existe pero esquemático y auto-declarado provisional) — fila 14a.
- Simulaciones (existe pero sin tipado de interés ni bonificaciones ni ICO) — fila 17.
- Dossier (existe pero sin ZIP documental y con narrativa mínima) — filas 18, 22.
- Precondiciones de transición (1 de ~7) — fila 10a.
- Narrativa (AI provider = NoOp) — fila 22.
- Catálogo de tipo de inmueble (texto libre) — fila 25.
- Seed de bancos (mínimo dev) — fila 17e.

## 5. Funcionalidades Legacy `OBSOLETO` / `ROTO` / a `DESCARTAR`

- `mortgage_holders` (tabla muerta).
- `reports/bank/generate.php`, `reports/client/generate_pdf.php` (rotos: método inexistente).
- `index.php` duplicado de `dashboard.php`; `calculateFinalInterest` duplicada; `CLIENT_SCORING` sin uso.
- Replicación **física** de documentos cliente→casos (`ClientDocumentsSyncService`) — fuente de bugs; el modelo relacional lo hace innecesario.
- Estructura **física** de carpetas `01…06` — sustituida por catálogo de tipos + versionado.
- `case_document_checklist.status` (`recibido`/`pendiente`) con endpoint sin UI (`toggle_document.php`).
- Contraseña inicial fija `changeme123`; `display_errors=1`; credenciales de BD en claro — ya resueltos por diseño en la Nueva.
- Umbrales numéricos de scoring de la Legacy (65/35, ≥60, ≥70, tramos de ingresos/ahorro) — **arbitrarios y sin base documentada**; no migrar tal cual (ver `BRIKKA_V2_BUSINESS_RULES_GAP.md`).
- Gate de honorarios por tipo de inmueble con importes fijos (500/1.500/2.000/750/2.250) — es la **tarifa de un intermediario concreto**, no una regla de producto; no migrar como regla del sistema.

## 6. Nuevas capacidades de la Nueva Brikka que la Legacy NO tenía (conservar, NO tocar)

Motor de **matching bancario** determinista (criterios versionados, overrides auditados) ·
pipeline **BankRequest → BankResponse → BankOffer → FinalFinancing** con snapshot de contacto ·
**Portal Cliente** como límite de seguridad separado (JWT propio) · **conversaciones/mensajes** con
adjuntos y participantes · **notificaciones** in-app/email (RabbitMQ opcional) · **tareas** ·
**planes/entitlements/suscripciones** · **Activity feed** + **audit events** separados ·
**versionado documental** + revisión + publicación selectiva al portal · **extracción documental
por IA** (worker Python aislado, Anthropic o **Ollama local**) · **procedencia e historial** en
perfil financiero y honorarios · **multi-tenant** resuelto en servidor · `CaseAssignment` y
`participation_type` · **Flyway**, **CI real**, imágenes Docker no-root, `ProdEnvironmentValidator`
fail-closed.

## 7. Acciones a migrar / mejorar / rediseñar (consolidado — tras decisiones §10)

| Acción | Elementos (todos dentro de I1–I5) |
|---|---|
| **MIGRAR** (traer valor Legacy que falta, sin copiar código) | Seed de `document_requirements` por tipo de operación (I1) · seed de ruleset de scoring + 3 categorías (I2) · bonificaciones de simulación **aplicándolas de verdad** (I4) · flag ICO (I4) · ZIP de documentos del caso (I5) |
| **MEJORAR** (ya existe, elevar antes de dar por bueno) | Checklist: auto-generación + **cierre por revisión/aprobación** + vista de completitud (I1) · Indicador **RAG cualitativo** a nivel de caso combinando scoring + viabilidad DTI + completitud documental (I2) · Precondiciones de transición: de 1 a **3** (I3) · Simulación: tipo de interés `FIXED/VARIABLE/MIXED` + desglose euríbor/diferencial (I4) · Dossier: incluir ZIP documental (I5) · Narrativa **determinista** del dossier (I5) |
| **REDISEÑAR** | Ninguno. La arquitectura moderna se conserva íntegra. |
| **DESCARTAR** | Todo lo de §5 (incluye, confirmado: score de cliente por puntos, ponderación 65/35, gate numérico de transición, importes fijos de honorarios, cierre de requisito por "existe archivo"). |
| **FUTURO** (documentado, no se implementa) | Contratos legales (Información Previa y Honorarios · Contrato de Intermediación LCCI · Autorización RGPD) · datos legales de `Company` (representante, tipo jurídico, logo) · render a PDF real · tarifas de honorarios por empresa + motor de devengo · toda la IA real (OCR, clasificación, extracción, detección de inconsistencias, resumen, asistente) · campos de inmueble (condición, tipología de ubicación) · catálogo cerrado de tipo de inmueble · seed de ~45 bancos españoles + criterios por banco · score combinado único / gate por score · precondiciones de transición adicionales · informe de viabilidad para cliente como PDF independiente · unicidad parcial de `document_number` |

Ver la lista completa y motivada en `BRIKKA_V2_MIGRATION_SCOPE.md §6`.

---

## 8. Priorización (Fase 5 — cerrada con decisiones §10)

### P0 — IMPRESCINDIBLE
- **I1 · Checklist documental**: seed de `document_requirements` (`PURCHASE`) + auto-generación de `document_requests` al entrar en `DOCUMENTATION` + **cierre del requisito solo con `review_status = APPROVED`** + endpoint/vista de completitud. Diseño AI-ready (sin implementar IA).

### P1 — IMPORTANTE (entra en V2)
- **I2 · Scoring de fábrica + RAG**: migración que siembra un `scoring_ruleset` `ACTIVE` + 3 categorías (`SOLIDO/VIGILANCIA/BLOQUEADO`, umbrales en el ruleset); indicador **RAG cualitativo** del caso = peor eje de {categoría de scoring, categoría de viabilidad DTI, completitud del checklist}.
- **I3 · Precondiciones de transición** (3): `DOCUMENTATION→ANALYSIS` (checklist obligatorio aprobado), `BANK_SEARCH→BANK_SUBMISSION` (≥1 solicitud bancaria válida), `OFFER→FORMALIZATION` (oferta seleccionada). Con excepción autorizada (permiso + motivo). Sin gate numérico.
- **I4 · Simulación enriquecida**: `interest_type` `FIXED/VARIABLE/MIXED` + desglose euríbor/diferencial + **bonificaciones aplicadas al tipo** + cuota recalculada con `MortgagePaymentCalculator` + flag `ico_guarantee`.
- **I5 · Dossier + ZIP + narrativa**: descarga ZIP (streaming) de todos los documentos del caso + narrativa **determinista** por reglas.

### P2 — dentro de bloque, no bloqueante
- Flag ICO (dentro de I4).
- Persistencia de `base_interest_rate` / `final_interest_rate` y bonificaciones aplicadas (dentro de I4).

### DESCARTADA (para V2)
- Carpetas físicas 01–06 · replicación física de documentos · `mortgage_holders` · endpoints rotos · pack de identificación / "ficha del titular" · contraseña `changeme123` · **score de cliente por puntos** · **ponderación 65/35** · **gate de transición por número de score** · **honorarios con importes fijos** de un intermediario concreto · cierre de requisito documental por mera presencia de archivo.

### FUTURO (documentado en `BRIKKA_V2_MIGRATION_SCOPE.md §6`, no se implementa)
- Contratos legales (los 3 documentos LCCI/RGPD) — bloqueado por ausencia de clausulado aprobado.
- Datos legales de `Company` (representante legal, tipo jurídico, logo) — dependían de los contratos.
- Render a **PDF real** (Gotenberg/OpenPDF/Flying Saucer).
- Tarifas de honorarios por empresa + motor de devengo/facturación (reglas temporales R23).
- **Toda la IA real**: OCR, clasificación automática de documentos, extracción IA → perfil financiero, detección de inconsistencias por IA, resumen IA de expediente, asistente IA.
- Campos de inmueble: condición de conservación, tipología de ubicación; catálogo cerrado de tipo de inmueble.
- Seed de ~45 entidades bancarias españolas + `bank_criteria_versions` por banco.
- Score combinado único del expediente / gate de transición por score.
- Precondiciones de transición adicionales a las 3 de I3.
- Informe de viabilidad para el cliente como documento PDF independiente.
- Unicidad parcial de `document_number` de cliente por empresa (regla R05).

### REQUIERE DECISIÓN
- **Ninguna pendiente.** Todas las decisiones de alcance están resueltas en `BRIKKA_V2_MIGRATION_SCOPE.md §10`. Quedan solo decisiones **de diseño interno** que se resuelven dentro de cada sprint (p. ej.: columnas nuevas vs `metadata` tipado en I4; `ico_guarantee` en `cases` vs `properties`; dossier+ZIP como descarga combinada vs enlace).

---

*Este documento es la Fase 2. La Fase 3 (reglas de negocio) está en `BRIKKA_V2_BUSINESS_RULES_GAP.md`.
El alcance cerrado y aprobado está en `BRIKKA_V2_MIGRATION_SCOPE.md`. El seguimiento vivo está en
`BRIKKA_V2_MIGRATION_PROGRESS.md`. No se ha modificado código de negocio hasta este punto.*
