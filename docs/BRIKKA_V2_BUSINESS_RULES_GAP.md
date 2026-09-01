# BRIKKA V2 — GAP DE REGLAS DE NEGOCIO (LEGACY → NUEVA BRIKKA)

> **Actualizado con las decisiones definitivas del propietario** (`BRIKKA_V2_MIGRATION_SCOPE.md §10`):
> - R08 (gate numérico), **R09** (score de cliente por puntos), **R11** (ponderación 65/35) →
>   `DESCARTAR` **confirmado**. La evaluación de cliente la cubre el análisis DTI/viabilidad ya
>   existente; no se mantienen dos sistemas de scoring.
> - R08 revisada → **3 precondiciones cualitativas** (I3): DOCUMENTATION→ANALYSIS,
>   BANK_SEARCH→BANK_SUBMISSION, OFFER→FORMALIZATION.
> - R14 → decidido: requisito completo solo con **revisión/aprobación** (`SUBIDO → APROBADO`).
> - R22 (clausulado LCCI), R23 (reglas temporales de honorarios), R16 (tarifas por empresa) →
>   `FUTURO` **confirmado** (I6 fuera de V2).
>
> ---
>
> **Base de análisis.**
> Reglas extraídas de `docs/LEGACY_FORENSIC_AUDIT.md §12` y del código Legacy
> (`config/scoring.php`, `config/case_status.php`, `config/document_checklist.php`,
> `config/helpers.php`, `services/CaseContractsService.php`, `cases/simulations/*`), comparadas con
> la implementación real de la Nueva Brikka.
>
> **Principio aplicado (instrucción del encargo):** *no se asume que una regla Legacy sea correcta.*
> Varias reglas Legacy son **arbitrarias, sin base documentada y con bugs**; se marcan como tales y
> se propone alternativa.

Leyenda de **Decisión**: `ADOPTAR` (traer la regla) · `ADOPTAR-REVISADA` (traer el concepto, cambiar
los valores/mecánica) · `YA CUBIERTA` · `DESCARTAR` · `REQUIERE DECISIÓN`.

---

## R01 — Aislamiento multi-tenant por empresa

| | |
|---|---|
| **Regla Legacy** | Manager/broker solo ven datos de su `company_id`; broker solo sus propios casos; superadmin todo. Aplicado "a mano" en cada consulta. |
| **Implementación Legacy** | `WHERE company_id = ?` repetido en ~12 endpoints (fácil de olvidar → IDOR real, S03 de la auditoría). |
| **Nueva Brikka** | `TenantContext` resuelto server-side desde la identidad; nunca se confía en `company_id` del cliente; `BRK-002`, `BRK-019` con tests de aislamiento; `SUPPORT_SESSION` como único acceso de SUPERADMIN a recursos de tenant. |
| **Diferencias** | La Nueva lo resuelve **estructuralmente**; la Legacy caso por caso. |
| **Decisión** | `YA CUBIERTA` (mejor). | 
| **Prioridad** | — |

## R02 — Empresa desactivada bloquea el acceso

| | |
|---|---|
| **Regla Legacy** | Si `role != superadmin` y `company.active = 0` → login denegado. |
| **Nueva Brikka** | `Company.status`; el flujo de auth valida estado. Además hay planes/suscripciones. |
| **Decisión** | `YA CUBIERTA`. |
| **Prioridad** | — |

## R03 — Límite de usuarios por empresa

| | |
|---|---|
| **Regla Legacy** | No crear usuarios si `users_count >= user_limit` (3/10/∞), contando solo activos. |
| **Nueva Brikka** | Modelo de **planes + entitlements + suscripciones** (`ADR-PLATFORM-001`): la autorización de una funcionalidad limitada por plan requiere `tenant + permission + entitlement`. |
| **Diferencias** | La Nueva generaliza "qué ha contratado la empresa"; el "límite de N usuarios" concreto no está confirmado como entitlement. |
| **Decisión** | `FUTURO`. No entra en V2. Si el negocio quiere un tope de asientos, modelarlo como entitlement de plan (**no** como columna `user_limit`). |
| **Prioridad** | FUTURO |

## R04 — Jerarquía de creación de usuarios

| | |
|---|---|
| **Regla Legacy** | superadmin → cualquier rol; manager → solo broker; manager no cambia rol ni empresa. |
| **Nueva Brikka** | RBAC con 110 permisos × 4 roles (`14_DEFINITIVE_PERMISSION_CATALOG.md`, `ADR-RBAC-001`). |
| **Decisión** | `YA CUBIERTA` (verificar que el permiso de alta de MANAGER esté restringido a SUPERADMIN; muy probablemente sí). |
| **Prioridad** | — |

## R05 — Unicidad de DNI de cliente por empresa

| | |
|---|---|
| **Regla Legacy** | `dni` (en mayúsculas, `trim`) único dentro de la empresa. |
| **Nueva Brikka** | `Client.documentNumber` **sin restricción de unicidad** (comentario explícito en `Client.java`: *"No uniqueness constraint is placed on the document yet"*). |
| **Diferencias** | La Nueva no impide duplicados. |
| **Decisión** | `FUTURO` (definitivo, alineado con `BRIKKA_V2_MIGRATION_SCOPE.md §4.3 / §6` y con las tablas de resumen de este documento §5). **Fuera del alcance cerrado I1–I5** (decisión del propietario §10.9): es una mejora de integridad, no un hueco funcional Legacy. Si en el futuro se aborda: unicidad **parcial** `(company_id, document_type, document_number)` cuando `document_number` no es nulo, normalizando a mayúsculas/sin espacios en el servicio. |
| **Prioridad** | P2 (FUTURO) |

## R06 — Unicidad de email de usuario

| | |
|---|---|
| **Regla Legacy** | Email global único. |
| **Nueva Brikka** | `users.email` gestionado por identity/auth; unicidad esperada. |
| **Decisión** | `YA CUBIERTA` (verificar índice único). |
| **Prioridad** | — |

## R07 — Flujo de estados del expediente

| | |
|---|---|
| **Regla Legacy** | `nuevo → {en_estudio, rechazado}` → `documentacion` → `ofertado` → `cerrado`; `rechazado`/`cerrado` terminales. 6 estados. |
| **Implementación Legacy** | `CASE_STATUS_FLOW` + `canChangeCaseStatus()`. |
| **Nueva Brikka** | `CaseWorkflow` + `CaseStatus` (10 estados: `PRESTUDY, DOCUMENTATION, ANALYSIS, BANK_SEARCH, BANK_SUBMISSION, BANK_REVIEW, OFFER, FORMALIZATION, COMPLETED, CANCELLED`), `13_DEFINITIVE_WORKFLOW_SPECIFICATION.md`, historial completo + eventos de dominio + reapertura auditada. |
| **Diferencias** | La Nueva es un superconjunto más granular. El mapa conceptual: `nuevo→PRESTUDY`, `en_estudio→ANALYSIS` (o `PRESTUDY`), `documentacion→DOCUMENTATION`, `ofertado→OFFER`, `cerrado→COMPLETED`, `rechazado→CANCELLED`. |
| **Decisión** | `YA CUBIERTA` (mejor). |
| **Prioridad** | — |

## R08 — Puertas de transición por scoring numérico

| | |
|---|---|
| **Regla Legacy** | Pasar a `ofertado` exige score total ≥ 60; a `cerrado` ≥ 70 (`scoringAllowsStatus()`). |
| **Valoración** | Los umbrales 60/70 **no tienen base documentada**; el score que los alimenta usa pesos arbitrarios (ver R09–R11). Bloquear el cierre de una operación por un número inventado es **cuestionable**. |
| **Nueva Brikka** | No existe. `13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §5` lista *precondiciones recomendadas* (documentación mínima, análisis suficiente, ≥1 entidad objetivo, ≥1 respuesta compatible, oferta seleccionada, checklist de formalización) — **solo se implementa "≥1 cliente antes de DOCUMENTATION"**. |
| **Decisión** | `DESCARTAR` el gate por número de score (definitivo, §10.1). En su lugar (I3, §10.2), implementar **3 precondiciones cualitativas**: `DOCUMENTATION→ANALYSIS` (checklist obligatorio **aprobado**), `BANK_SEARCH→BANK_SUBMISSION` (≥1 solicitud/relación bancaria válida), `OFFER→FORMALIZATION` (oferta seleccionada / `final_financing`). Con excepción autorizada (permiso + motivo). Sin gates adicionales. |
| **Prioridad** | P1 (I3) · gate numérico → DESCARTADO |

## R09 — Score de cliente (ingresos, ahorro, antigüedad, penalización por deudas)

| | |
|---|---|
| **Regla Legacy** | Base 50; +20/+12/+6/−5 por ingresos (≥3000/≥2000/≥1500/resto); +15/+10/+5 por ahorro; +10/+6/+3 por antigüedad; −3/−6/−10 por otros préstamos; −3 si hay tarjetas; *clamp* [40,100]. Varios titulares: 70 % del mejor + 30 % media del resto. |
| **Valoración** | Tramos y pesos **arbitrarios, sin fuente**. La regla "70/30 entre titulares" tampoco tiene base. La auditoría Legacy ya lo señala. |
| **Nueva Brikka** | El motor de scoring (`ScoreField`) **solo admite 5 campos**: `TERM_MONTHS, REQUESTED_AMOUNT, VALUATION, PURCHASE_PRICE, LTV`. **No puede** referenciar ingresos/ahorro/antigüedad/deudas. En su lugar existe `financialanalysis` (Sprint 31): **DTI** = (deudas mensuales + cuota) / ingresos × 100, clasificado en `FAVORABLE` (≤35 %) / `REVISAR` (≤40 %) / `NO_VIABLE`, **por cliente**, con `rules_version`, disclaimer explícito y umbrales configurables por properties. |
| **Diferencias** | La Legacy hace un "sistema de puntos opaco"; la Nueva hace un **ratio único, estándar de mercado (DTI), explicable y configurable**. `ViabilityClassifier.java` documenta explícitamente que se descartó reusar el motor de puntos para esto. |
| **Decisión** | `DESCARTAR` (definitivo, §10.1). El **análisis DTI/viabilidad es la sustitución oficial** de la dimensión de cliente. No se mantienen dos sistemas de scoring paralelos. Cualquier señal de cliente adicional se define de novo con el negocio (FUTURO). |
| **Prioridad** | DESCARTADO |

## R10 — Score del inmueble por LTV

| | |
|---|---|
| **Regla Legacy** | `ltv = loan_amount / property_value`: ≤0,60→100; ≤0,70→90; ≤0,80→75; ≤0,90→60; resto→30; 0 si faltan datos. |
| **Nueva Brikka** | `ScoreInputSnapshotFactory` calcula `ltv = requestedAmount / MIN(valuation, purchasePrice)` (escala 4, HALF_UP, fallback al denominador que exista, null si falta) — **idéntico en espíritu, más robusto** (usa el mínimo de valoración/precio, alineado con `ADR-BANKENGINE-001 D-A`). Los tramos concretos son **configurables por ruleset**, no hardcoded. |
| **Diferencias** | La Nueva parametriza los tramos; la Legacy los fija. La Nueva usa `MIN(valoración, precio)` como denominador (más conservador y correcto). |
| **Decisión** | `YA CUBIERTA` (mejor). Acción asociada: **sembrar un ruleset por defecto** con tramos LTV razonables para que produzca resultado de fábrica (ver `BRIKKA_V2_FUNCTIONAL_GAP.md` fila 11d). |
| **Prioridad** | P1 (seed) |

## R11 — Score combinado del expediente (ponderación 0,65 cliente + 0,35 inmueble)

| | |
|---|---|
| **Regla Legacy** | `caseScore = round(0,65·clientScore + 0,35·propertyScore)`. |
| **Valoración** | Ponderación **sin fuente**. Combina dos números que ya son arbitrarios. |
| **Nueva Brikka** | No existe un score único combinado. Hay: (a) `scoring_results` (inmueble/operación, por ruleset), (b) `case_financial_analysis_results` (DTI/viabilidad, por cliente). Deliberadamente separados. |
| **Decisión** | `DESCARTAR` la ponderación numérica (definitivo, §10.1). El indicador único de caso (I2) es un **RAG cualitativo** = peor de {categoría de scoring de operación/inmueble, categoría de viabilidad DTI, completitud del checklist obligatorio}, con umbrales en el `scoring_ruleset` (editables), nunca en código. Sin medias ponderadas. |
| **Prioridad** | P1 (I2) |

## R12 — Semáforo del expediente (rojo / ámbar / verde)

| | |
|---|---|
| **Regla Legacy** | Rojo si checklist incompleto **o** score < 55 ("Bloqueado"); ámbar si score ≤ 70 ("En vigilancia"); verde si > 70 ("Sólido"). |
| **Nueva Brikka** | `ScoringEngine.resolveCategory()` resuelve una `category` (nombre libre) por umbral `maxScore` configurado en el ruleset — **es el mismo concepto**, genérico y versionado. Pero **no hay ruleset ni categorías sembradas**, y **no hay indicador a nivel de caso** que combine scoring + viabilidad + documentación. |
| **Diferencias** | El concepto está; falta la instancia por defecto y la agregación a nivel de expediente. |
| **Decisión** | `ADOPTAR-REVISADA`: sembrar un ruleset con 3 categorías (`SOLIDO/VIGILANCIA/BLOQUEADO` o equivalente i18n) y exponer un **indicador RAG del caso** = combinación cualitativa de (categoría de scoring, categoría de viabilidad DTI, checklist documental completo). Sin números mágicos: los umbrales viven en el ruleset y son editables. |
| **Prioridad** | P1 |

## R13 — Checklist documental auto-generado al crear el expediente

| | |
|---|---|
| **Regla Legacy** | Al crear el caso se insertan en `case_document_checklist`: por titular `dni, nomina, vida_laboral` (obl.) + `irpf, contrato_trabajo, otros_ingresos` (opc.); del expediente `nota_simple, arras` (obl.) + `tasacion, oferta_bancaria` (opc.). |
| **Nueva Brikka** | `document_requirements` (V5): catálogo **condicional por `operation_type`** (`mandatory`, `conditions` jsonb). `document_requests` puede referenciar un `requirement_id`. **Pero:** ninguna migración siembra requisitos, y `CaseService.createCase()` **no genera** requests. Los tipos ya existen en el catálogo (`V2`: `DNI, NIE, PAYSLIP, INCOME_TAX_RETURN, EMPLOYMENT_HISTORY, BANK_STATEMENT, EMPLOYMENT_CONTRACT, DEPOSIT_CONTRACT, LAND_REGISTRY_EXTRACT, PROPERTY_APPRAISAL`). |
| **Diferencias** | La Nueva tiene un modelo **mejor** (condicional, catalogado, por tipo de operación) pero **sin datos ni enganche**. |
| **Decisión** | `ADOPTAR-REVISADA`: (1) sembrar `document_requirements` para `PURCHASE` con el mapa Legacy traducido a los códigos `V2` (`dni→DNI`, `nomina→PAYSLIP`, `vida_laboral→EMPLOYMENT_HISTORY`, `irpf→INCOME_TAX_RETURN`, `contrato_trabajo→EMPLOYMENT_CONTRACT`, `nota_simple→LAND_REGISTRY_EXTRACT`, `arras→DEPOSIT_CONTRACT`, `tasacion→PROPERTY_APPRAISAL`; "oferta bancaria" pasa a ser `BankOffer`, no documento); (2) auto-crear `document_requests` al entrar el caso en `DOCUMENTATION` (o al crearlo), distinguiendo requisito **por titular** vs **del expediente**; (3) endpoint/vista de **completitud del checklist** del caso. |
| **Prioridad** | **P0** |

## R14 — Un requisito se cumple si existe el documento del tipo

| | |
|---|---|
| **Regla Legacy** | El checklist marca "recibido" por **presencia de fichero** del tipo (`client_documents`/`case_documents`), no por un flag manual. |
| **Nueva Brikka** | `documents` tienen `status` y `review_status`; `document_requests` tienen `status` (`PENDING`...). Falta la regla que **cierra** un requisito cuando se sube/aprueba el documento de ese tipo. |
| **Decisión** | `ADOPTAR-REVISADA` (definitivo, §10.3): un requisito **NO** se cierra por la mera existencia de un archivo. Ciclo `(sin documento) → SUBIDO → REVISADO/APROBADO → COMPLETO`. El requisito solo pasa a completo cuando la `DocumentVersion` casante alcanza `review_status = APPROVED` (reutilizando el flujo de revisión existente). Un documento subido sin aprobar deja el requisito visible como `SUBIDO / pendiente de revisión`. Diseño **AI-ready**: el enganche "documento → requisito (tipo + titular)" debe ser un punto de extensión, para que una clasificación/extracción por IA pueda proponerlo en el futuro sin tocar el dominio (no se implementa IA ahora). |
| **Prioridad** | **P0** (I1) |

## R15 — Replicación de documentos cliente → casos

| | |
|---|---|
| **Regla Legacy** | Al subir un documento de cliente se **copia físicamente** a `03_ANALISIS` de todos sus casos y se hace *upsert* en `case_documents`. |
| **Valoración** | Mecanismo frágil (rutas con/sin fecha, copias huérfanas) — la auditoría lo marca como fuente de bugs. |
| **Nueva Brikka** | Documentos versionados en MinIO, relación lógica caso↔documento; sin copias. |
| **Decisión** | `DESCARTAR`. La "visibilidad del documento del cliente en el caso" se resuelve por consulta, no por copia. |
| **Prioridad** | — |

## R16 — Cálculo de honorarios

| | |
|---|---|
| **Regla Legacy** | Vivienda `habitual` → 500 inicio / 1.500 éxito (2.000 si algún titular es `autonomo`); otros inmuebles → 750 / 2.250. |
| **Valoración** | Son **importes concretos de la tarifa de un intermediario** (`Israel Muela`, ver `contrato_intermediacion.php`), **no una regla de producto**. La auditoría lo señala. |
| **Nueva Brikka** | `casefee` (V25): `FIXED` (importe) o `PERCENTAGE` (base × % / 100), `calculated_amount` determinista en el backend, estados `PROPOSED/AGREED/CANCELLED`, historial append-only. `EngagementContractService` ya inserta los honorarios en el contrato. |
| **Diferencias** | La Nueva es genérica y correcta; no propone importes automáticamente. |
| **Decisión** | `DESCARTAR` los importes fijos como regla del sistema (definitivo, §10.7). El modelo `casefee` actual es suficiente para V2. **Plantillas de tarifa por empresa** y motor de devengo → `FUTURO`. |
| **Prioridad** | FUTURO |

## R17 — Regeneración de contratos en cada edición

| | |
|---|---|
| **Regla Legacy** | Los 3 PDF legales se **borran y regeneran** en cada alta/edición del caso y al añadir titular. |
| **Nueva Brikka** | `EngagementContractService.generate()` crea **una `DocumentVersion` nueva** cada vez (nunca sobrescribe) — el histórico es cada versión del fichero. Se invoca **bajo demanda**, no automáticamente en cada edición. |
| **Diferencias** | La Nueva **no** regenera automáticamente (evita churn) y **conserva** versiones anteriores (mejor trazabilidad). |
| **Decisión** | `YA CUBIERTA` (mejor). No adoptar el borrado+regeneración automática. |
| **Prioridad** | — |

## R18 — Interés base de la simulación por tipo

| | |
|---|---|
| **Regla Legacy** | `fijo` → tipo fijo; `variable` → diferencial + euríbor; `mixto` → tipo fijo del tramo (exige años fijos < años totales). |
| **Nueva Brikka** | `Simulation(interestRate)` — un único número, sin tipo ni desglose. `MortgagePaymentCalculator` calcula la cuota (sistema francés) a partir de `principal/rate/term`. |
| **Diferencias** | La Nueva no modela `FIXED/VARIABLE/MIXED` ni el desglose euríbor+diferencial ni el tramo mixto. |
| **Decisión** | `ADOPTAR-REVISADA`: añadir `interest_type` (`FIXED/VARIABLE/MIXED`) + campos de desglose (`spread`, `euribor`, `fixed_years`, `fixed_rate`, `variable_spread`) en `simulations.metadata` o columnas nuevas; derivar `interestRate` efectivo y calcular la cuota con el calculador existente. Mantener la validación "años fijos < años totales". |
| **Prioridad** | P1 |

## R19 — Bonificaciones que reducen el tipo de interés

| | |
|---|---|
| **Regla Legacy** | `tipo_final = tipo_base − Σ(bonificaciones activas: nómina, seguro hogar, seguro vida, alarma, tarjeta, inversiones, otras)`, con *floor* 0. |
| **Estado en Legacy** | **BUG confirmado**: las columnas `bonus_*` se guardan pero `calculateFinalInterest` **nunca se invoca** desde el alta/edición; `final_interest_rate` se guarda igual al base. Además la función está **declarada dos veces** con firmas distintas (fatal si se cargan juntas). |
| **Nueva Brikka** | No existe el concepto. |
| **Decisión** | `ADOPTAR-REVISADA` (sin reproducir el bug): modelar un conjunto de bonificaciones (catálogo cerrado + tasa por bonificación), calcular `tipo_final = max(0, tipo_base − Σ tasas activas)` **en el backend**, recalcular la cuota, y mostrar base vs final. Persistir base, bonificaciones aplicadas y final. |
| **Prioridad** | P1 |

## R20 — Oferta seleccionada excluyente

| | |
|---|---|
| **Regla Legacy** | Solo `is_offer=1`; al seleccionar una se ponen `selected=0` en el resto del caso. |
| **Nueva Brikka** | Separación limpia: `BankOffer` (N por caso) + `FinalFinancing` (**1 por caso**, `final_financing` referencia una `bank_offer_id`). `FinancialAnalysisService` ya prioriza la `FinalFinancing` sobre las simulaciones. |
| **Decisión** | `YA CUBIERTA` (mejor: la exclusividad es estructural vía `FinalFinancing`). |
| **Prioridad** | — |

## R21 — Borrado de cliente restringido

| | |
|---|---|
| **Regla Legacy** | No se puede borrar un cliente con registros en `case_clients`. |
| **Nueva Brikka** | No verificado un borrado duro de cliente; el modelo usa `status`. |
| **Decisión** | `FUTURO` (mejora de integridad, no hueco funcional Legacy). Si en algún momento se añade borrado de cliente: impedirlo para clientes vinculados a casos no terminados; usar cambio de `status`. |
| **Prioridad** | FUTURO |

## R22 — Declaración de independencia / cumplimiento LCCI

| | |
|---|---|
| **Regla Legacy** | Textos fijos en contratos e informes: comparativa objetiva "sin incentivos ni comisiones de entidades"; exclusividad 12 meses; honorario de éxito solo si se formaliza en notaría; marco legal Ley 5/2019, RD 309/2019, RGPD, Ley 10/2010 (PBC/FT). |
| **Nueva Brikka** | `EngagementContractService` genera un HTML **esquemático** con disclaimer *"documento técnico... no constituye un contrato jurídicamente válido... debe ser sustituido por el clausulado legal aprobado por la empresa"*. El clausulado real **no está**. |
| **Diferencias** | La Legacy tiene clausulado LCCI real y redactado; la Nueva es un placeholder honesto. |
| **Decisión** | `FUTURO` (definitivo, §10.4). I6 queda **fuera de V2**. No se genera ni se inventa clausulado jurídico. Queda documentada la necesidad futura de los 3 documentos (Información Previa y Honorarios, Contrato de Intermediación LCCI, Autorización RGPD), parametrizados desde `Company` (que a su vez necesitará campos de representante legal / tipo jurídico / logo — también `FUTURO`, §10.6). Se retoma cuando exista clausulado aprobado por la empresa. |
| **Prioridad** | FUTURO |

## R23 — Reglas temporales de honorarios

| | |
|---|---|
| **Regla Legacy** | Honorario de inicio no reembolsable tras iniciar; éxito exigible si se firma en los 3 meses siguientes al desistimiento; prórroga trimestral con preaviso de 15 días; duración 12 meses. |
| **Nueva Brikka** | No modelado (el `casefee` solo tiene `PROPOSED/AGREED/CANCELLED` + `agreed_at`). |
| **Decisión** | `FUTURO` (con R22/I6 y con el motor de devengo, §10.4/§10.7). No entra en V2. |
| **Prioridad** | FUTURO |

## R24 (nueva, no estaba en Legacy) — LTV como `requestedAmount / MIN(valuation, purchasePrice)`

| | |
|---|---|
| **Regla Nueva** | `ADR-BANKENGINE-001 D-A` y `ScoreInputSnapshotFactory`: denominador = mínimo de valoración y precio de compra. |
| **Comparación** | La Legacy usaba `loan_amount / property_value` (solo valor). La Nueva es **más conservadora y correcta**. |
| **Decisión** | `MANTENER` la regla Nueva. Si se siembra un ruleset de scoring con tramos LTV, usar esta definición. |
| **Prioridad** | — |

---

## Resumen de decisiones sobre reglas (cerrado — §10 del alcance)

| Decisión | Reglas |
|---|---|
| **YA CUBIERTA (igual o mejor) — MANTENER** | R01, R02, R04, R06, R07, R10, R17, R20, R24 |
| **ADOPTAR-REVISADA en V2 (concepto sí; valores/mecánica revisados)** | R12 (RAG cualitativo, umbrales en ruleset → **I2**), R13 (checklist condicional + auto-gen → **I1**), R14 (cierre por revisión/aprobación + AI-ready → **I1**), R18 (tipo de interés FIXED/VARIABLE/MIXED → **I4**), R19 (bonificaciones aplicadas de verdad, sin el bug → **I4**) |
| **ADOPTAR-REVISADA — precondiciones** | R08 revisada → **3 gates cualitativos** con excepción autorizada (**I3**) |
| **ADOPTAR (regla de integridad / texto)** | R21 (impedir borrado de cliente vinculado, si existe borrado) |
| **DESCARTAR (definitivo)** | R08 (gate numérico ≥60/≥70), R09 (score de cliente por puntos), R11 (ponderación 65/35), R15 (replicación física de documentos), R16 (importes fijos de tarifa como regla del sistema) |
| **FUTURO (documentado, fuera de V2)** | R16 (tarifas por empresa + motor de devengo), R22 (clausulado LCCI / Información Previa / RGPD), R23 (reglas temporales de honorarios) |
| **FUTURO / mejora de integridad (no hueco Legacy)** | R03 (tope de asientos como entitlement), R05 (unicidad parcial de `document_number`) |
| **REQUIERE DECISIÓN pendiente** | Ninguna. Solo quedan decisiones de diseño interno resueltas dentro de cada sprint. |

**Prioridad agregada (alcance V2 = I1–I5):**
- **P0** → R13, R14 (I1).
- **P1** → R10 seed + R12 (I2) · R08-revisada (I3) · R18, R19 (I4) · narrativa determinista (I5).
- **DESCARTADO** → R08 numérica, R09, R11, R15, R16 (importes fijos).
- **FUTURO** → R16 (tarifas por empresa), R22, R23, R03, R05, R21.
