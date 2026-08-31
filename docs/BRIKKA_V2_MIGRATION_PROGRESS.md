# BRIKKA V2 — SEGUIMIENTO DE LA MIGRACIÓN (LEGACY → BRIKKA V2)

> Documento **vivo**. Se actualiza al cerrar cada tarea/bloque. El porcentaje refleja **trabajo real
> completado y validado** (backend + frontend + tests en verde), no estimaciones optimistas.
>
> Alcance: `BRIKKA_V2_MIGRATION_SCOPE.md` (I1–I5; I6 → FUTURO). La migración **termina** cuando I1–I5
> cumplen la condición de §7 de ese documento y se declara **MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA**.

---

## Barra de progreso global

```
V2  [██░░░░░░░░░░░░░░░░░░░]   ~5 %   (0 / 5 bloques · V2-0 cerrado)   —   fase: I1 (Sprint V2-1)
```

| Bloque | Peso | Estado | Backend | Frontend | Tests BE | Tests FE | % bloque |
|---|---:|---|---|---|---|---|---:|
| **I1** · Checklist documental | 25 % | ⬜ Pendiente | ⬜ | ⬜ | ⬜ | ⬜ | 0 % |
| **I2** · Scoring de fábrica + RAG | 20 % | ⬜ Pendiente | ⬜ | ⬜ | ⬜ | ⬜ | 0 % |
| **I3** · Precondiciones de transición | 15 % | ⬜ Pendiente | ⬜ | ⬜ | ⬜ | ⬜ | 0 % |
| **I4** · Simulación enriquecida | 25 % | ⬜ Pendiente | ⬜ | ⬜ | ⬜ | ⬜ | 0 % |
| **I5** · Dossier + ZIP + narrativa | 15 % | ⬜ Pendiente | ⬜ | ⬜ | ⬜ | ⬜ | 0 % |
| **TOTAL** | 100 % | | | | | | **0 %** |

Leyenda: ⬜ pendiente · 🟨 en curso · ✅ completado y validado.

---

## Bloque / sprint actual

**V2-1 · I1 · Checklist documental** (en curso).

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

### I1 · Checklist documental  *(P0 — Sprint V2-1)*

**Definition of Done:**
- [ ] Migración `V27__seed_document_requirements.sql` con el mapa `PURCHASE` (por titular:
      `DNI`, `PAYSLIP`, `EMPLOYMENT_HISTORY` obl.; `INCOME_TAX_RETURN`, `EMPLOYMENT_CONTRACT`,
      `BANK_STATEMENT` opc. — de expediente: `LAND_REGISTRY_EXTRACT`, `DEPOSIT_CONTRACT` obl.;
      `PROPERTY_APPRAISAL` opc.).
- [ ] Auto-generación idempotente de `document_requests` al entrar el caso en `DOCUMENTATION`
      (por titular vs de expediente).
- [ ] Cierre del requisito **solo** con `review_status = APPROVED` de la versión casante; estado
      intermedio `SUBIDO` visible.
- [ ] `CaseChecklistService` + endpoint de completitud (obligatorios/opcionales, por titular y de
      expediente, con estado por requisito).
- [ ] Vista de checklist en `case-detail` (Angular) integrada con guards y manejo de errores.
- [ ] Punto de extensión "documento → requisito (tipo + titular)" como interfaz (AI-ready, sin IA).
- [ ] Tests BE: auto-gen idempotente · requisito NO se cierra al subir, SÍ al aprobar · aislamiento
      de tenant.
- [ ] Tests FE: componente de checklist.
- [ ] Batería completa en verde.
- [ ] `docs/` y este PROGRESS actualizados.

**Criterio de aceptación:** al pasar un caso `PURCHASE` a `DOCUMENTATION` aparecen los requisitos;
subir el documento del tipo correcto → requisito `SUBIDO`; aprobarlo → `APROBADO`; la vista muestra
"faltan N obligatorios".

### I2 · Scoring de fábrica + indicador RAG  *(P1 — Sprint V2-3)*

**Definition of Done:**
- [ ] Migración `V28__seed_default_scoring_ruleset.sql`: `scoring_ruleset` `ACTIVE`
      (`default-property-v1`) + reglas LTV / importe / plazo + 3 categorías
      (`SOLIDO`/`VIGILANCIA`/`BLOQUEADO`, `maxScore` configurable + catch-all).
- [ ] `CaseIndicatorService` + endpoint de solo lectura: RAG = **peor** de {categoría de
      `scoring_result`, categoría de `case_financial_analysis_result`, completitud de checklist
      obligatorio (I1)}. Ejes ausentes → "sin evaluar".
- [ ] Badge en `case-detail` y `case-list` (`shared/status-badge`).
- [ ] Tests BE: resolución de categoría con el ruleset sembrado · combinación RAG completa/parcial/
      ausente · tenant.
- [ ] Tests FE: badge.
- [ ] Batería completa en verde.

**Criterio de aceptación:** LTV bajo + viabilidad FAVORABLE + checklist completo → verde; cualquier
eje en rojo → rojo; sin datos → "sin evaluar".

### I3 · Precondiciones de transición  *(P1 — Sprint V2-2)*

**Definition of Done:**
- [ ] `DOCUMENTATION → ANALYSIS`: exige checklist documental **obligatorio aprobado** (I1).
- [ ] `BANK_SEARCH → BANK_SUBMISSION`: exige ≥1 solicitud/relación bancaria válida (`bank_requests`).
- [ ] `OFFER → FORMALIZATION`: exige oferta seleccionada (`final_financing` → `bank_offer_id`).
- [ ] Excepción autorizada: permiso específico + **motivo obligatorio** en `case_status_history.reason`.
- [ ] Sin gate numérico de score. Sin gates adicionales.
- [ ] Tests BE por transición: bloqueada / permitida / con excepción · error estructurado
      `{code, message, requestId}`.
- [ ] `change-status-dialog` (Angular) refleja el motivo de bloqueo y la opción de excepción.
- [ ] Batería completa en verde.

**Criterio de aceptación:** no se avanza si la precondición falla; con permiso + motivo sí, y queda
auditado.

### I4 · Simulación enriquecida  *(P1 — Sprint V2-4)*

**Definition of Done:**
- [ ] Migración `V29__simulation_interest_type_and_bonifications.sql`: `interest_type`
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

| Ámbito | Baseline (V2-0, 2026-08-31) | Actual | Nuevos en V2 |
|---|---|---|---|
| Frontend (`ng lint` + `npm test`) | lint ✅ · **485/485** (97 ficheros) | 485/485 | 0 |
| Backend unit (Surefire, sin Docker) | **106/106** ✅ | 106/106 | 0 |
| Backend integración (Failsafe, Colima) — muestra V2-0 | `FlywayMigrationIT` + `CaseServiceIT` = **26/26** ✅ | 26/26 | 0 |
| Backend integración — suite completa `./mvnw verify` | *(no ejecutada en V2-0; gate de cierre)* | — | — |
| Aislamiento de tenant (por recurso nuevo) | n/a | — | 0 / 5 recursos nuevos |

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

---

## Declaración de cierre

> Pendiente. Se emitirá **MIGRACIÓN LEGACY → BRIKKA V2 COMPLETADA** cuando la barra global esté al
> 100 % (I1–I5 con DoD cumplida y batería completa en verde) y este documento y `CHANGELOG.md` /
> `docs/12_DECISION_LOG.md` estén actualizados. A partir de esa declaración **no se añaden más
> funcionalidades de migración**; todo lo nuevo va a `BRIKKA_V2_MIGRATION_SCOPE.md §6 FUTURO`.
