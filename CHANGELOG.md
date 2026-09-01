# Changelog

Formato basado en el historial real del repositorio y en el código verificado — sin
funcionalidades inventadas. Ver `docs/12_DECISION_LOG.md` para el detalle sprint a sprint y
`docs/30_RELEASE_V1.0.0.md` para la definición formal de la release V1.0.0.

## [2.0.0] — BRIKKA V2 (migración funcional Legacy → V2)

> Versión candidata. Trabajo en la rama `feat/v2-migration`, **pendiente de merge a `main` y de
> tag `v2.0.0`** (operaciones posteriores con confirmación explícita del propietario, mismo
> criterio que los Gates 23/25 de la release V1.0.0).

Migración funcional controlada **Legacy → BRIKKA V2**: cierre de los huecos funcionales
verificados de la Brikka Legacy que no cubría V1, **sin copiar código PHP y sin reproducir bugs
de Legacy**. Alcance cerrado y aprobado por el propietario: bloques **I1–I5**. Ver
`docs/BRIKKA_V2_MIGRATION_SCOPE.md` (alcance), `docs/BRIKKA_V2_MIGRATION_PROGRESS.md`
(seguimiento) y `docs/BRIKKA_V2_FINAL_AUDIT.md` (auditoría final).

### Producto

- **I1 — Checklist documental del expediente.** Catálogo `document_requirements` condicional por
  tipo de operación con semilla para `PURCHASE` (por titular y del expediente); auto-generación
  idempotente de `document_requests` al entrar el caso en `DOCUMENTATION`; un requisito solo se
  cierra con la versión del documento en `review_status = APPROVED` (no por la mera existencia de
  un archivo); endpoint y vista de completitud del checklist en `case-detail`. El enganche
  "documento → requisito (tipo + titular)" queda como punto de extensión AI-ready, **sin IA**.
- **I2 — Scoring "de fábrica" + indicador RAG del expediente.** Migración que siembra un
  `scoring_ruleset` `ACTIVE` (`default-operation-v1`) para el motor de scoring existente, con
  reglas y umbrales **en datos** (jsonb), nunca en código; 3 categorías `GREEN` / `AMBER` / `RED`.
  Indicador RAG cualitativo a nivel de caso = **peor** de {categoría del scoring de operación,
  peor viabilidad DTI por titular, completitud del checklist obligatorio}; determinista; ejes
  ausentes → "sin evaluar". Endpoint `GET /api/v1/cases/{caseId}/scoring/rag` (permiso reutilizado
  `SCORING_READ`) y visualización en `case-detail`. **No se recupera** la fórmula Legacy de score
  de cliente, ni la ponderación 65/35, ni ningún gate de transición por número de score.
- **I3 — Precondiciones de transición del expediente.** Tres gates de negocio validados en el
  backend: `DOCUMENTATION → ANALYSIS` (checklist obligatorio aprobado), `BANK_SEARCH →
  BANK_SUBMISSION` (≥ 1 solicitud bancaria del caso), `OFFER → FORMALIZATION` (oferta seleccionada
  vía `final_financing`). Excepción autorizada: permiso nuevo `CASE_TRANSITION_OVERRIDE`
  (MANAGER / SUPERADMIN) + **motivo obligatorio**, registrado en `case_status_history.reason` con
  marcador `[PRECONDITION_OVERRIDE] ` y en el evento de auditoría existente. Sin gates adicionales.
- **I4 — Simulación hipotecaria enriquecida.** Tipo de interés `FIXED` / `VARIABLE` / `MIXED`;
  Euríbor + diferencial; tramo fijo en `MIXED`; **bonificaciones que reducen de verdad el tipo**
  (`tipo_final = max(0, tipo_base − Σ bonificaciones activas)`) — Legacy las guardaba pero no las
  aplicaba (bug); tipo base y tipo final persistidos; **cuota recalculada en el backend** con el
  calculador de amortización francés existente (para `MIXED`, cuota del tramo fijo + cuota del
  tramo variable re-amortizando el saldo pendiente); flag `ico_guarantee` en `simulations`. Sin
  segundo motor de cálculo. **No se recuperan** de Legacy `monthly_payment_phase2`,
  `total_interest` ni `recommended`.
- **I5 — ZIP documental del expediente + narrativa determinista del dossier.** Descarga
  `GET /api/v1/cases/{caseId}/documents/archive`: ZIP **en streaming** (un documento en memoria a
  la vez, sin fichero temporal) con la versión actual de cada documento del caso, estructura
  interna derivada de metadatos (`<tipo>/<titular | "expediente">/<id>-<nombre>`, **no** las
  carpetas físicas Legacy `01–06`), nombres saneados (sin path traversal), control de acceso
  `DOCUMENT_DOWNLOAD` + aislamiento de tenant. El `ViabilityDossierService` se **eleva**: su HTML
  deja de ser un volcado de campos y pasa a ser una narrativa determinista de 8 secciones
  (situación, titulares, inmueble, financiación, scoring/RAG, viabilidad, documentación,
  honorarios) construida **exclusivamente** a partir de datos almacenados; endpoint de solo
  lectura `GET /api/v1/cases/{caseId}/dossier/narrative`. Un dato ausente se indica explícitamente,
  nunca se inventa. **Sin IA, sin `AiProvider`, sin Ollama.**

### Técnico

- Base de datos: se pasa de **26 a 30 migraciones Flyway** (V1–V30). Las 4 nuevas de V2 son todas
  **aditivas y no destructivas**: `V27__document_checklist.sql` (I1 — `documents.client_id` nullable + índice,
  `UNIQUE(operation_type, document_type_id)`, semilla de requisitos `PURCHASE`),
  `V28__case_transition_override_permission.sql` (I3 — 1 permiso + 2 `role_permissions`),
  `V29__seed_default_scoring_ruleset.sql` (I2 — 1 `scoring_rulesets` + 4 `scoring_rules`),
  `V30__simulation_interest_type_and_bonifications.sql` (I4 — 9 columnas en `simulations` con
  `CHECK`/`DEFAULT`/backfill). **Ninguna tabla nueva.**
- RBAC: **un único permiso nuevo en todo V2** — `CASE_TRANSITION_OVERRIDE` (I3), asignado a
  MANAGER y SUPERADMIN. El resto de bloques reutiliza permisos existentes
  (`DOCUMENT_REQUEST`, `DOCUMENT_READ`, `DOCUMENT_DOWNLOAD`, `SCORING_READ`, `SCORING_RUN`,
  `SIMULATION_CREATE`, `SIMULATION_READ`).
- API: 8 endpoints nuevos o extendidos, todos con control de acceso `tenant + rol + asignación`
  (caso de otro tenant → 404) y contrato de error estándar `{code, message, requestId}`. **Sin
  APIs paralelas.**
- Cálculo financiero en `BigDecimal` (`numeric(7,4)` tipos, `numeric(14,2)` dinero), sin
  `double`/`float`; redondeos explícitos `HALF_UP`. `MortgagePaymentCalculator` movido del paquete
  `financialanalysis` a `financing` (evita un ciclo de paquetes) y ampliado con
  `computeOutstandingBalance`.
- **Sin IA real en esta versión.** El scoring es reglado y sus umbrales viven en el ruleset; la
  narrativa del dossier es determinista y por reglas. No se introduce ninguna dependencia de
  proveedor de IA. Extracción/OCR/clasificación documental por IA, IA de resumen/explicación del
  scoring y del dossier, y proveedor Ollama quedan **explícitamente FUTURO**
  (`docs/BRIKKA_V2_MIGRATION_SCOPE.md §6`).
- **Fuera del alcance V2** (registrado como FUTURO, no implementado): contratos legales I6
  (Información Previa y Honorarios, Contrato de Intermediación LCCI, Autorización RGPD) y los
  campos de `Company` que dependen de ellos; render a PDF real; tarifas de honorarios
  configurables por empresa y motor de devengo; seed de ~45 entidades bancarias; indicador RAG en
  `case-list` (requeriría un endpoint de lote); campos de inmueble (condición de conservación,
  tipología de ubicación); unicidad parcial de `document_number` de cliente por empresa (R05).

## [1.0.0]

Cierre oficial de Brikka V1.

### Producto

- Gestión multiempresa (multi-tenant), resuelta desde la identidad autenticada.
- Usuarios y RBAC: SUPERADMIN, MANAGER, BROKER, CLIENT (portal como límite de seguridad separado).
- Gestión de clientes (CRM).
- Casos/expedientes hipotecarios, con historial de estado.
- Financiación: simulaciones, ofertas.
- Motor de matching bancario y solicitudes a bancos.
- Comisiones de caso (case fees).
- Análisis financiero y dossier de viabilidad.
- Contratos de encargo (engagement contract).
- Gestión documental: versionado, subida, descarga vía URL presignada, no se sobrescriben
  versiones históricas.
- Extracción de documentos asistida por IA (AI Worker), transporte local por defecto, transporte
  HTTP opcional.
- Tareas.
- Notificaciones (transporte síncrono por defecto; RabbitMQ como transporte asíncrono opcional).
- Catálogo de planes.
- Actividad y auditoría.

### Técnico

- Backend: Spring Boot 3.5.16 (Java 21).
- Frontend: Angular 22.1.
- Base de datos: PostgreSQL 16, migraciones Flyway (26 migraciones).
- Almacenamiento: MinIO (S3-compatible).
- Cola de mensajes: RabbitMQ 3.13 (opcional).
- Autenticación propia: JWT RS256 autoemitido (sin Keycloak), claves internal/portal
  independientes, refresh tokens rotativos con detección de reuso.
- Hashing de contraseñas: Argon2id.
- Multi-tenancy resuelto server-side, nunca confiando en `company_id` del cliente.
- CI real en GitHub Actions: build + tests + lint + build de imagen Docker backend + escaneo Trivy.
- Imágenes Docker backend y frontend, ambas ejecutando como usuario no-root (validado en
  ejecución real, no solo en el Dockerfile).
- Validación fail-closed de configuración de producción (`ProdEnvironmentValidator`): aborta el
  arranque en el perfil `prod` si faltan secretos críticos (claves JWT, credenciales de
  MinIO/RabbitMQ/base de datos, SMTP) o si CORS/seed quedan en un estado inseguro.

### Corregido

- `HttpMessageNotReadableException` (body de request ausente o JSON malformado) devolvía `500
  INTERNAL_ERROR` en cualquiera de los 32 endpoints con `@RequestBody`, en vez de `400
  BAD_REQUEST`. Ahora responde `400` con el contrato de error estándar de la API
  (`{"code":"INVALID_REQUEST", "message", "requestId"}`), reutilizando el mismo formato que el
  resto de errores 4xx.
