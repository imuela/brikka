# 27 — SPRINT 22: ANÁLISIS Y DISEÑO DE SUSTITUCIÓN DE KEYCLOAK POR AUTENTICACIÓN PROPIA

**Estado del documento: ANÁLISIS. Ninguna decisión aquí está aprobada por sí sola.**

Este informe es el entregable único del Sprint 22, un sprint **de solo análisis y diseño**, sin
ninguna implementación. No se ha modificado ni un solo archivo de código, configuración, Docker,
base de datos, ni se ha realizado ningún commit durante su elaboración. Todo lo descrito en las
secciones 18-23 (diseño conceptual, migración, plan por fases) es una **propuesta para discusión**,
no un plan en ejecución.

---

## 1. Resumen ejecutivo

Brika usa hoy Keycloak (2 realms: `brika` para usuarios internos, `brika-portal` para el Portal
Cliente) exclusivamente como **verificador de credenciales y emisor de identidad** (Authorization
Code + PKCE, tokens JWT firmados). El backend **no confía en ningún claim del JWT salvo el
`sub`** — rol, permisos, empresa (tenant) y todo dato de autorización se resuelven en PostgreSQL a
partir de `external_identity_id`. Esto es el hallazgo más importante de este análisis: **Keycloak
hoy no transporta ninguna decisión de negocio**, lo cual reduce drásticamente el radio de impacto
de sustituirlo, comparado con una arquitectura donde roles/claims/grupos estuvieran mapeados desde
el IdP.

El coste real de sustitución no está en RBAC ni en tenant isolation (ya son 100% propios), sino en
tres piezas que Keycloak sí resuelve hoy y que habría que reconstruir: (a) el protocolo
OAuth2/OIDC completo del lado servidor (emisión/firma/rotación de tokens, endpoints
`.well-known`, revocación), (b) el **almacenamiento y verificación de contraseñas** (hoy no existe
en absoluto en el esquema de Brika — riesgo de migración de primer orden), y (c) la superficie
administrativa (alta/baja/reset de usuarios) que hoy vive en la consola de Keycloak.

Se recomienda (ver §17) una arquitectura de **autenticación propia stateless con JWT firmados por
Brika**, reutilizando sin cambios toda la capa `SecurityConfig`/`*JwtAuthenticationConverter`/
`TenantContext`/`AuthorizationService` existente, sustituyendo únicamente el emisor de tokens. Es
la opción de menor radio de impacto de las cuatro evaluadas.

---

## 2. Alcance y método de este análisis

**Alcance:** inventario completo de todo lo que depende de Keycloak (backend, frontend, base de
datos, Docker, tests, documentación), evaluación de qué debe sustituirse y cómo, y una
recomendación de arquitectura con plan de implementación. **No incluye implementación.**

**Método:**
- Lectura directa y completa del núcleo de seguridad del backend: `SecurityConfig.java`,
  `BrikaJwtAuthenticationConverter.java`, `PortalJwtAuthenticationConverter.java`,
  `AuthorizationService.java`, `LazyIssuerJwtDecoder.java`, `JwtAudienceValidator.java`,
  `TenantContext.java`, `MeController.java`, `User.java`, `UserRepository.java`,
  `UserProvisioningService.java`, `BrikaAuthenticationToken.java`,
  `PortalAuthenticationToken.java`, `PortalJwtDecoder.java`, `PermissionResolutionService.java`.
- Lectura directa de configuración: `application.yml`, `.env.example`, `docker-compose.yml`,
  `keycloak/brika-realm.json`, `keycloak/brika-portal-realm.json`,
  `keycloak/themes/brikka/login/**`.
- Tres auditorías dirigidas (agentes de solo lectura) sobre: (1) superficie de auth del frontend,
  (2) esquema de BD y tests relacionados con auth, (3) documentación (`19_IDENTITY_OAUTH_
  SPECIFICATION.md`, `06_SECURITY_SPECIFICATION.md`, ADRs de `12_DECISION_LOG.md`,
  `25_CLAUDE_CODE_EXECUTION_GUIDE.md`, `09_ROADMAP.md`, `GETTING_STARTED.md`,
  `14_DEFINITIVE_PERMISSION_CATALOG.md`) e infraestructura Docker/CI.
- Grep exhaustivo de "password"/"keycloak"/"oidc" en `backend/src/main/java` y
  `backend/src/main/resources/db/migration` para confirmar ausencia total de almacenamiento de
  credenciales propio.

---

## 3. Arquitectura actual de autenticación/autorización (end-to-end)

**Frontend → Keycloak (navegador):**
1. El usuario pulsa "Iniciar sesión". El frontend genera `code_verifier`/`code_challenge` (PKCE,
   Web Crypto API, sin librería OIDC de terceros) y `state`, los guarda en `sessionStorage`, y
   redirige a Keycloak (`/realms/{realm}/protocol/openid-connect/auth`).
2. Keycloak autentica al usuario (usuario/contraseña, alojados y verificados por Keycloak) y
   redirige de vuelta con un `code`.
3. El frontend intercambia el `code` + `code_verifier` por un `access_token`/`refresh_token` en
   `/protocol/openid-connect/token`. Los tokens se guardan **solo en memoria** (`signal<TokenSet|
   null>`), nunca en `localStorage`/`sessionStorage`.
4. Un interceptor HTTP añade `Authorization: Bearer <access_token>` a cada petición, distinguiendo
   la pila interna de la de Portal Cliente únicamente por prefijo de URL (`/api/v1/portal/`).
5. No hay `APP_INITIALIZER` de rehidratación: un F5 pierde la sesión en memoria (trade-off
   documentado explícitamente en el código).

**Backend (Spring Security OAuth2 Resource Server, stateless):**
1. Dos `SecurityFilterChain` independientes: `@Order(1)` para `/api/v1/portal/**` (realm
   `brika-portal`), `@Order(2)` para el resto (realm `brika`). Cada uno con su propio
   `JwtDecoder` (`LazyIssuerJwtDecoder`, descubrimiento OIDC estándar vía
   `JwtDecoders.fromIssuerLocation()`) y su propio `Converter<Jwt, AbstractAuthenticationToken>`.
2. El decoder valida firma, issuer, expiración y (si está configurada) audiencia
   (`JwtAudienceValidator`) — es decir, hace todo el trabajo criptográfico de verificar que el
   token es genuino y vigente.
3. El converter **ignora cualquier claim de rol/permiso/empresa**. Solo lee `jwt.getSubject()` y
   busca `external_identity_id = sub` en `users` (interno) o `client_portal_accounts` (Portal).
   Si no hay coincidencia → `401 BadCredentialsException`. Si hay coincidencia, construye
   `BrikaAuthenticationToken`/`PortalAuthenticationToken` con el usuario/cuenta cargado de
   PostgreSQL.
4. `AuthorizationService`/`PermissionResolutionService` resuelven permisos vía
   `user_roles → role_permissions → permissions` en PostgreSQL — cero relación con Keycloak.
5. `TenantContext.resolve(role, companyId)`: SUPERADMIN → sin tenant; el resto → su `companyId`
   propio de BD.

**Conclusión de la sección:** Keycloak participa únicamente en el paso "¿es esta persona quien
dice ser, y aquí tienes un identificador (`sub`) estable para ella?". Todo lo demás —quién es en
términos de negocio, qué puede hacer, en qué empresa opera— es 100% de Brika.

---

## 4. Inventario completo de dependencias de Keycloak

**Backend:**
- `spring.security.oauth2.resourceserver.jwt.issuer-uri` (`application.yml:22`) y
  `brika.security.portal-issuer-uri` (`:35`) — descubrimiento OIDC en tiempo de arranque (lazy).
- `brika.security.expected-audience` / `portal-expected-audience` (vacíos por defecto — validación
  de audiencia no forzada hoy).
- `LazyIssuerJwtDecoder`, `PortalJwtDecoder` (wrapper de composición, no de herencia — ver
  `PortalJwtDecoder.java`), ambos basados en `JwtDecoders.fromIssuerLocation()` de Spring Security
  OAuth2, que en arranque hace una llamada HTTP a Keycloak (`/.well-known/openid-configuration`).
- Nada más en el backend depende de Keycloak: no hay llamadas a la Admin API, no hay
  aprovisionamiento automático de usuarios en Keycloak (`UserProvisioningService.createUser()`
  exige que el `externalIdentityId` ya exista, creado manualmente por el MANAGER).

**Frontend:**
- Flujo completo Authorization Code + PKCE hecho a mano (sin `keycloak-js`/`angular-oauth2-oidc`/
  `oidc-client-ts` en `package.json`).
- Dos pilas paralelas independientes: `AuthService`/`SessionStore`/`auth.guard.ts` (interno) vs.
  `PortalAuthService`/`PortalSessionStore`/`portal-auth.guard.ts` (Portal), sin código compartido.
- URLs de Keycloak embebidas en `environment.ts` (`FRONTEND_OIDC_ISSUER` y equivalente Portal).
- Redirect URIs registradas en los clients Keycloak: `http://localhost:4200/*` (interno) y
  `http://localhost:4200/portal/*` (Portal).

**Base de datos:**
- Columna `external_identity_id` en `users` (línea 43 de `16_POSTGRESQL_SCHEMA_SPECIFICATION.md`)
  y en `client_portal_accounts` (línea 130) — **sin constraint UNIQUE** (riesgo señalado y sin
  resolver desde ADR-IDENTITY-001).
- Ninguna tabla almacena contraseñas, hashes, tokens de refresco, ni sesiones. Confirmado por grep
  exhaustivo de "password" en todo `backend/src/main/java` y `db/migration`: cero coincidencias
  fuera de comentarios/ADRs.

**Docker/infraestructura:**
- Servicio `identity` en `docker-compose.yml` (imagen `quay.io/keycloak/keycloak:26.0`,
  `start-dev --import-realm`, puertos `18081`/`19090`, volumen de datos con nombre, montajes
  read-only de `keycloak/brika-realm.json`, `keycloak/brika-portal-realm.json` y
  `keycloak/themes/`).
- Ningún otro servicio de compose declara `depends_on` sobre `identity` — no hay acoplamiento a
  nivel de compose, solo a nivel de configuración de aplicación (`OIDC_ISSUER_URI`/
  `OIDC_PORTAL_ISSUER_URI` en `.env`).
- El backend **no corre en Docker** (se ejecuta con `mvn spring-boot:run` fuera de compose), así
  que no hay ninguna dependencia de red compose→compose que romper.

**Tests:**
- **Ningún test arranca un Keycloak real.** Todos los tests de integración (`*IT.java`,
  Testcontainers + PostgreSQL real) usan `StubJwtDecoderConfig`, una `@TestConfiguration` que
  confía literalmente en el string del bearer token como `sub`, razonando explícitamente que la
  validación de firma/issuer es "código bien probado de Spring Security, no nuestro" y por tanto
  fuera del alcance de los tests propios.
- CI (`.github/workflows/ci.yml`) no referencia Keycloak/OIDC en ningún punto — solo build, tests
  unitarios, Flyway vía Testcontainers, y format check.

**Documentación:**
- `19_IDENTITY_OAUTH_SPECIFICATION.md` es deliberadamente agnóstico de proveedor (nunca nombra
  "Keycloak" — habla de "proveedor OIDC-compatible"), lo cual **ya anticipa y facilita** este
  sprint: el diseño conceptual original nunca acopló el dominio a Keycloak por nombre.
- El acoplamiento concreto a Keycloak vive en ADRs de `12_DECISION_LOG.md`: **ADR-FRONTEND-001**
  (provisión inicial del realm `brika`), **ADR-PORTAL-AUTH-001** (frontera de seguridad Portal,
  con cláusula explícita de que romper esa frontera requiere excepción justificada — no aplica
  aquí porque la sustitución de IdP no toca la frontera, solo el emisor de tokens), y
  **ADR-PROCESS-007** (provisión del realm `brika-portal`, Sprint 19).

---

## 5. Qué aporta Keycloak hoy realmente (capacidades usadas)

1. Almacenamiento y verificación de contraseñas (hashing, políticas — ninguna política de
   complejidad configurada hoy en los realms, `passwordPolicy` está vacío en ambos JSON).
2. Emisión de JWT firmados (RS256 por defecto de Keycloak), con rotación de claves vía JWKS.
3. Flujo Authorization Code + PKCE (S256) estándar, con `standardFlowEnabled: true`,
   `implicitFlowEnabled: false`, `directAccessGrantsEnabled: false` en ambos clients — es decir,
   ya está configurado de forma restrictiva y correcta.
4. Expiración/rotación de `refresh_token` (parámetros por defecto de Keycloak — ningún
   `accessTokenLifespan`/`ssoSessionIdleTimeout`/`ssoSessionMaxLifespan` está sobreescrito en los
   realms, así que hoy se usan los valores por defecto de Keycloak 26, no unos elegidos por
   Brika).
5. Endpoint de logout con `post.logout.redirect.uris` configurado.
6. Console de administración para operaciones manuales (alta de usuario, reset de contraseña) —
   usada operativamente pero no integrada con el flujo de `UserProvisioningService` (ver §7).
7. Un tema de login personalizado (`keycloak/themes/brikka/`, trabajo de un sprint interrumpido
   anterior a este, en disco pero no verificado ni aplicado en vivo) — puramente cosmético, sin
   relevancia arquitectónica para este análisis.

---

## 6. Lo que Keycloak NO aporta (ya resuelto localmente)

- **RBAC / catálogo de permisos**: 100% en PostgreSQL (`14_DEFINITIVE_PERMISSION_CATALOG.md`,
  tablas `roles`/`permissions`/`user_roles`/`role_permissions`). Confirmado: el catálogo no
  menciona Keycloak ni una sola vez.
- **Resolución de tenant/empresa**: 100% en `TenantContext`, a partir de `users.company_id` en BD.
  `company_id` nunca se acepta como claim del cliente (cumple `19_IDENTITY_OAUTH_
  SPECIFICATION.md §7`).
- **Separación Portal Cliente / usuarios internos**: 100% arquitectónica (dos `SecurityFilterChain`
  físicamente distintos, dos tipos de principal que nunca se cruzan) — no depende de que Keycloak
  tenga dos realms separados, solo aprovecha esa separación como conveniencia operativa.
- **Multiempresa / aislamiento de tenant en datos**: RLS y filtros de aplicación en el dominio,
  sin ninguna relación con Keycloak.

**Implicación directa:** sustituir Keycloak es, en esencia, sustituir **un único componente con una
interfaz muy angosta** — "verifica credenciales, dame un `sub` fiable en un JWT firmado" — no una
reforma de la arquitectura de autorización.

---

## 7. Impacto en Backend

- `LazyIssuerJwtDecoder`/`PortalJwtDecoder` dejan de resolver el issuer por descubrimiento OIDC de
  un IdP externo; pasarían a validar tokens firmados por Brika (clave propia, rotación propia).
- `BrikaJwtAuthenticationConverter`/`PortalJwtAuthenticationConverter` **no cambian en su lógica**
  (siguen leyendo `sub` y resolviendo contra `users`/`client_portal_accounts`) — solo cambia de
  dónde viene el `Jwt` validado.
- `SecurityConfig` mantiene la misma forma (dos `SecurityFilterChain`, `@Order(1)`/`@Order(2)`).
- Nuevo componente requerido: un emisor de tokens propio (endpoint de login que verifique
  contraseña + emita JWT firmado), y su contraparte de administración (alta/reset de contraseña),
  hoy inexistentes.
- `UserProvisioningService` necesitaría dejar de asumir que `externalIdentityId` ya existe en un
  IdP externo creado manualmente — con auth propia, la creación de usuario y de credencial pueden
  unificarse en una sola operación, eliminando un punto de fricción operativa actual.
- Nueva superficie de riesgo que no existe hoy: almacenamiento de hashes de contraseña propio
  (algoritmo, coste de hashing, salting — hoy delegado por completo a Keycloak).

## 8. Impacto en Frontend

- El flujo PKCE hecho a mano ya está desacoplado de Keycloak como librería (no usa `keycloak-js`),
  así que la superficie de cambio es menor de lo que parece: hay que cambiar los endpoints
  (`/authorize`, `/token`) por los propios de Brika, pero la lógica de PKCE, `sessionStorage` para
  el `code_verifier`/`state`, y tokens en memoria puede conservarse.
- Las dos pilas paralelas (interna/Portal) requieren el mismo trabajo duplicado que cualquier
  cambio en esta área — es una constante estructural del proyecto, no específica de esta
  migración.
- Los interceptores HTTP (distinción por prefijo `/api/v1/portal/`) no cambian.
- Si se opta por un login con formulario propio (en vez de redirect a una pantalla externa), esto
  **sí es un cambio de UX** más visible: pantallas de login nuevas dentro del propio Angular, en
  vez de una redirección a Keycloak.

## 9. Impacto en Base de datos

- Nueva(s) tabla(s) requerida(s): credenciales (hash de contraseña, algoritmo, fecha de cambio),
  y probablemente tokens de refresco/sesiones si se opta por revocación server-side (ver §18).
- La columna `external_identity_id` puede conservarse como identificador estable (ya es la clave
  de búsqueda que usa todo el backend) o colapsarse en el propio `id` de usuario — es una decisión
  de diseño, no una necesidad técnica.
- **Recomendación técnica**: cerrar en esta migración el hueco ya señalado en ADR-IDENTITY-001
  (`external_identity_id` sin constraint UNIQUE), puesto que una auth propia hace de esa columna
  (o su sucesora) la clave de identidad primaria del sistema.

## 10. Impacto en Docker/Infraestructura

- El servicio `identity` (Keycloak) se retira de `docker-compose.yml` — libera el
  contenedor de mayor consumo de recursos del stack de desarrollo (ya identificado como fuente de
  contención de CPU en el propio compose, ver comentario sobre `rabbitmq` en `docker-compose.yml:
  35-43`).
- No hay que tocar ningún `depends_on` (no existen apuntando a `identity`).
- Variables de entorno a retirar de `.env.example`: `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD`,
  `KEYCLOAK_HTTP_PORT`, `KEYCLOAK_HEALTH_PORT`, `OIDC_ISSUER_URI`, `OIDC_PORTAL_ISSUER_URI`,
  `FRONTEND_OIDC_ISSUER` y equivalentes — sustituidas por configuración de clave/firma propia
  (p. ej. `JWT_SIGNING_KEY` o similar, gestionada como secreto, nunca en el repo).
- El tema de login personalizado (`keycloak/themes/`) queda obsoleto si se retira Keycloak —
  trabajo ya invertido que se perdería; relevante para la decisión de timing (ver §24).

## 11. Impacto en Tests

- Como ningún test arranca Keycloak real hoy, **el patrón de testing no se degrada**: la sustitución
  de `StubJwtDecoderConfig` por un stub equivalente (o real, dado que un emisor propio es código de
  Brika y por tanto sí "nuestro" bajo el mismo criterio que ya usan) es mecánica.
  Dato relevante para la decisión de diseño: si el emisor de tokens es propio, deja de aplicar el
  razonamiento actual de "no testeamos la librería de Spring Security" — sí habría que testear el
  propio emisor/firmado, con casos de expiración, firma inválida, y claims manipulados
  (requisito explícito de `CLAUDE.md §9`: "Security tests must include cross-tenant and
  unauthorized-access scenarios").
- Nuevos tests obligatorios: hashing/verificación de contraseña, emisión/validación de token
  propio, expiración, revocación (si aplica), rate-limiting de intentos de login.

## 12. Impacto en Portal Cliente (frontera de seguridad)

- **Ningún cambio en la frontera en sí** (ADR-PORTAL-AUTH-001 se mantiene íntegro): dos
  `SecurityFilterChain`, dos tipos de principal, ninguna posibilidad de que un JWT del realm
  interno autentique contra el filtro Portal o viceversa.
- Con auth propia, esa separación se preservaría emitiendo tokens con un claim de "audiencia
  lógica" (interno vs. portal) o, más simple y más fiel al patrón actual, manteniendo dos flujos de
  login completamente separados con claves de firma potencialmente distintas — replicando a
  propósito la separación física que hoy dan los dos realms.
- Riesgo a vigilar explícitamente: al fusionar la emisión de tokens en el propio backend de Brika,
  existe el riesgo de que un futuro desarrollador comparta código entre el emisor interno y el de
  Portal "por conveniencia". ADR-PORTAL-AUTH-001 ya lo prohíbe expresamente y esa cláusula debe
  extenderse sin ambigüedad al nuevo emisor de tokens.

## 13. Impacto en RBAC multiempresa

Ninguno. RBAC y resolución de tenant no tienen ninguna dependencia de Keycloak (ver §6). Este es
uno de los motivos centrales por los que el radio de impacto de esta migración es manejable.

---

## 14. Análisis de amenazas de seguridad (actual vs. propuesto)

| Amenaza | Con Keycloak (hoy) | Con auth propia (propuesto) |
|---|---|---|
| Robo de contraseña en tránsito/reposo | Mitigado por Keycloak (hashing probado, mantenido por terceros) | Responsabilidad 100% de Brika — requiere elegir algoritmo (Argon2id recomendado), salting, y auditoría propia |
| Fuerza bruta sobre login | `bruteForceProtected` **no está activado** en ninguno de los dos realms hoy (confirmado: `null` en ambos JSON) — es decir, Keycloak ya no está aportando esta protección en la práctica actual | Debe implementarse explícitamente (rate limiting, backoff, bloqueo temporal) — es una mejora neta si se hace bien, o una regresión si se omite |
| Fuga de clave de firma de JWT | Gestionada por Keycloak, rotación JWKS automática | Responsabilidad de Brika: gestión de secreto, rotación planificada |
| Cross-tenant / escalada de rol vía claims manipulados | No aplica — el backend ya ignora todos los claims salvo `sub` | Sin cambio, siempre que el nuevo emisor preserve el mismo contrato (`sub` como único claim de negocio) |
| Sesión Portal ↔ interna cruzada | Estructuralmente imposible (dos realms, dos decoders) | Debe preservarse activamente con dos flujos/claves separados (ver §12) |
| Disponibilidad del IdP | Punto único de fallo hoy (si Keycloak cae, nadie inicia sesión) | Mismo riesgo se traslada al propio backend de Brika — no mejora ni empeora, cambia de propietario |
| Superficie de ataque expuesta | Consola de administración de Keycloak, endpoints OIDC estándar (bien auditados por terceros) | Superficie nueva y propia: cada endpoint de auth nuevo es código sin historial de auditoría externa |

**Conclusión de la sección:** el mayor riesgo de seguridad no es arquitectónico sino de
**superficie nueva sin rodaje** — un emisor de tokens y almacén de contraseñas propios son
componentes de alta criticidad que hoy Brika no tiene que escribir ni mantener. Esto no es un
motivo para descartar la migración, pero sí para tratar la implementación con el mismo rigor que
`CLAUDE.md §9` exige ("Security tests must include cross-tenant and unauthorized-access
scenarios") y para no subestimar el esfuerzo de hacerlo bien (ver §21).

---

## 15. Matriz de sustitución de capacidades

| Capacidad de Keycloak | Sustituto propuesto | Complejidad |
|---|---|---|
| Almacenamiento/verificación de contraseña | Tabla propia + Argon2id/bcrypt | Media |
| Emisión de JWT firmado | Firmado propio (RS256 o HS256 con secreto rotado) | Media |
| Endpoint `/authorize` + PKCE | Endpoint de login propio en backend Brika; frontend conserva su lógica PKCE actual | Baja (frontend ya no depende de librería) |
| Endpoint `/token` (exchange code→token) | Endpoint propio equivalente | Baja |
| Refresh token | Tabla de refresh tokens propia (o JWT refresco de larga duración con rotación) | Media |
| Logout / revocación | Endpoint propio + lista de revocación o TTL corto en access token | Media |
| Expiración de sesión (idle/máxima) | Lógica propia (hoy ni siquiera Keycloak tiene valores custom — se usan los defaults) | Baja |
| Recuperación de contraseña | Flujo propio (token de un solo uso + email — requiere definir remitente/plantilla, hoy no existe ningún sistema de envío de email en el inventario auditado) | Media-Alta |
| Consola de administración (alta/reset manual) | Pantalla de administración propia o extensión de la ya existente en `features/users` | Baja (ya existe UI de gestión de usuarios) |
| MFA (capacidad, no usada activamente hoy) | Diferido — no es un requisito confirmado hoy, solo "debe poder habilitarse" en la spec | Fuera de alcance inicial |
| Tema de login personalizado | Reemplazado por pantalla de login Angular propia (con el design system ya implementado) | Baja — de hecho simplifica, elimina la necesidad de un tema Keycloak separado |

---

## 16. Comparativa de arquitecturas alternativas

**A. Autenticación propia stateless (JWT firmado por Brika, sin sesión server-side)**
Login propio, emisión de JWT firmado con clave de Brika, validación igual que hoy
(`JwtDecoder` pero apuntando a la clave propia en vez de a un issuer OIDC externo). Refresh
mediante refresh token de larga duración almacenado hasheado en BD, rotado en cada uso.
- Ventajas: cambio mínimo en `SecurityConfig`/converters/`TenantContext` (se reutiliza casi todo);
  sin dependencia de infraestructura externa; sin contenedor Keycloak.
- Desventajas: hay que construir y mantener el emisor de tokens, el hashing de contraseñas y la
  recuperación de contraseña desde cero.

**B. Autenticación propia con sesión server-side (cookie de sesión, no JWT)**
Login propio, pero en vez de JWT se usa una sesión guardada en BD/Redis y una cookie httpOnly.
- Ventajas: revocación instantánea (borrar la fila de sesión), sin necesidad de gestionar claves de
  firma ni rotación JWKS.
- Desventajas: **rompe el contrato stateless actual** (`ADR-FRONTEND-001`, `06_SECURITY_
  SPECIFICATION.md` — `allowCredentials(false)` explícitamente elegido porque "los tokens Bearer
  nunca son credenciales ambientales"); requiere reescribir todo el `SecurityConfig` (pasar de
  Resource Server a session-based), reescribir ambos converters, y reescribir el interceptor HTTP
  del frontend (cookies en vez de header `Authorization`). Cambia también el modelo CORS. Es la
  opción de mayor radio de impacto de las cuatro.

**C. Proveedor de identidad externo alternativo (gestionado, ej. Auth0/Cognito/Ory/Supabase Auth)**
Sustituir Keycloak self-hosted por un proveedor externo gestionado, manteniendo el mismo patrón
Resource Server (`JwtDecoder` por issuer) que ya existe hoy.
- Ventajas: menor esfuerzo de mantenimiento operativo que Keycloak self-hosted; el backend casi no
  cambia (mismo patrón de `issuer-uri`).
- Desventajas: introduce una dependencia externa de pago/SaaS y de red en producción (contradice el
  espíritu de `19_IDENTITY_OAUTH_SPECIFICATION.md §9`, "evitar acoplar el dominio a APIs
  propietarias del proveedor" — cambiar un proveedor propietario por otro no resuelve el problema
  de fondo que motiva este sprint); no resuelve el motivo real detrás de este sprint si el motivo
  es reducir dependencias externas u operativas (no confirmado explícitamente por el usuario — ver
  §23, pregunta abierta).

**D. Mantener Keycloak pero simplificar su despliegue (ej. modo producción en vez de `start-dev`,
o gestionarlo como imagen propia versionada)**
No sustituye Keycloak, solo endurece su configuración operativa actual (hoy corre en
`start-dev`, sin `passwordPolicy`, sin `bruteForceProtected`, sin límites de sesión explícitos —
ver §14).
- Ventajas: coste de implementación mínimo; resuelve las brechas de seguridad reales detectadas en
  §14 sin ningún riesgo de migración.
- Desventajas: no cumple el objetivo explícito del sprint ("sustitución de Keycloak por
  autenticación propia") si ese es un objetivo firme y no solo exploratorio.

---

## 17. Recomendación técnica de arquitectura

**Se recomienda la opción A — autenticación propia stateless con JWT firmado por Brika.**

Motivos:
1. Es la única opción, de las que sustituyen Keycloak, que **no toca** el contrato stateless ya
   aprobado (`ADR-FRONTEND-001`) ni el `SecurityConfig`/converters/`TenantContext`/
   `AuthorizationService` existentes — el radio de impacto se concentra en un componente nuevo y
   acotado (emisor de tokens), no en una reforma transversal.
2. Preserva intacta la separación Portal Cliente/interno (§12), que es una decisión de seguridad ya
   aprobada y con cláusula de no-debilitamiento explícita (ADR-PORTAL-AUTH-001).
3. Elimina el contenedor Keycloak (mayor consumo de recursos del stack de desarrollo) sin introducir
   una nueva dependencia externa de pago (opción C) ni una reforma arquitectónica de sesión
   (opción B).
4. Es coherente con el principio ya escrito en la propia especificación
   (`19_IDENTITY_OAUTH_SPECIFICATION.md §9`): evitar acoplamiento a APIs propietarias — de hecho la
   opción A es la que mejor cumple ese principio de las cuatro, porque no depende de ningún
   proveedor externo en absoluto.

La opción D (endurecer Keycloak) es una alternativa razonable **si el objetivo real fuera solo
mitigar los hallazgos de seguridad de §14** sin sustituir el proveedor — se señala como pregunta
abierta en §23 porque el motivo de negocio detrás del sprint no está confirmado en el brief
recibido.

---

## 18. Diseño conceptual de autenticación propia — SOLO CONCEPTUAL, SIN IMPLEMENTAR

*(Recordatorio: esta sección es una propuesta de diseño para discusión, no un plan aprobado ni en
curso de implementación.)*

- **Login**: `POST /api/v1/auth/login` (y equivalente `/api/v1/portal/auth/login`) recibe
  usuario/contraseña, verifica contra el hash almacenado (Argon2id), si es válido emite un
  `access_token` (JWT firmado, TTL corto — p. ej. 15 min) y un `refresh_token` (opaco, almacenado
  hasheado en BD, TTL largo — p. ej. 30 días, rotado en cada uso).
- **Logout**: invalida el `refresh_token` actual en BD (borrado o marcado usado); el
  `access_token` en curso expira por TTL corto, sin necesidad de lista de revocación para el caso
  común (trade-off ya aceptado hoy con Keycloak en la práctica, dado que no hay revocación
  instantánea de access tokens ya emitidos).
- **Expiración/refresh**: el frontend detecta un `401` por token expirado y usa el
  `refresh_token` para obtener un nuevo `access_token` de forma transparente — mismo patrón que
  cualquier flujo OAuth2 estándar, sin cambio de UX percibido.
- **Cambio de contraseña**: endpoint autenticado que verifica la contraseña actual antes de
  aceptar la nueva; invalida todos los `refresh_token` existentes del usuario tras el cambio
  (defensa estándar: si la contraseña se filtró, cambiarla corta cualquier sesión robada).
- **Recuperación de contraseña**: token de un solo uso con TTL corto (p. ej. 1 hora), enviado por
  email — **requiere decidir y aprobar un mecanismo de envío de correo**, hoy inexistente en el
  inventario auditado (ninguno de los tres dominios auditados menciona un servicio de email
  saliente). Esto es una **dependencia nueva no trivial** que debe señalarse como decisión propia
  (ver §24).
- **MFA**: fuera del alcance inicial recomendado (no hay evidencia de que esté en uso hoy pese a
  estar disponible en Keycloak) — se deja como extensión futura, no como bloqueante de esta
  migración.

---

## 19. Estrategia de migración de usuarios

**Hallazgo crítico**: Brika no almacena ni ha almacenado nunca una contraseña localmente. Todas las
contraseñas de usuarios existentes (internos y Portal) viven exclusivamente en Keycloak y **no son
recuperables en texto plano ni migrables por hashing** (Keycloak no expone el hash en un formato
reutilizable vía API estándar sin acceso directo a su base de datos interna, lo cual además sería
frágil ante cambios de versión).

**Implicación directa**: la migración de usuarios existentes **exige un flujo de restablecimiento
obligatorio de contraseña** para toda la base de usuarios activa en el momento del corte (todos los
usuarios internos + todas las cuentas de Portal Cliente), no una migración transparente. Esto es
una decisión con impacto de negocio/soporte (comunicación a clientes del Portal) y **requiere
autorización explícita** (ver §24) antes de fijarse como parte del plan.

Alternativa mitigadora: emitir tokens de "establece tu contraseña" (equivalente al flujo de
recuperación de §18) de forma proactiva para todos los usuarios existentes en el momento del
corte, en vez de bloquear el acceso hasta que el usuario lo solicite reactivamente. Reduce fricción
pero requiere el mismo mecanismo de envío de email, con el mismo prerequisito no resuelto.

---

## 20. Plan de implementación por fases (propuesta, sujeta a aprobación)

1. **Fase 1 — Cimientos**: tabla de credenciales, hashing (Argon2id), emisor/validador de JWT
   propio, configuración de clave de firma como secreto (nunca en repo).
2. **Fase 2 — Endpoints de auth interno**: login/logout/refresh/cambio de contraseña para el realm
   "interno" únicamente (paridad con `brika-frontend` de hoy). Backend: nuevo controlador +
   servicio; `SecurityConfig`/converter interno apuntando al nuevo `JwtDecoder` propio.
3. **Fase 3 — Endpoints de auth Portal**: réplica de la Fase 2 para el Portal Cliente, preservando
   la separación física de ADR-PORTAL-AUTH-001 (claves/flujos distintos).
4. **Fase 4 — Frontend**: adaptar `AuthService`/`PortalAuthService` a los nuevos endpoints propios;
   nuevas pantallas de login Angular (sustituyen la redirección a Keycloak).
5. **Fase 5 — Recuperación de contraseña**: requiere resolver primero la dependencia de envío de
   email (decisión pendiente, §24).
6. **Fase 6 — Migración de usuarios existentes**: ejecución del flujo de restablecimiento
   obligatorio (§19) sobre la base de usuarios real, coordinada con soporte/comunicación a
   clientes del Portal.
7. **Fase 7 — Retirada de Keycloak**: eliminar servicio `identity` de `docker-compose.yml`,
   limpiar variables de entorno, archivar (no borrar sin política, `CLAUDE.md §3`) los archivos
   `keycloak/*.json` y el tema de login personalizado.
8. **Fase 8 — Endurecimiento**: rate limiting de login, políticas de expiración de sesión
   explícitas, tests de seguridad exhaustivos (cross-tenant, fuerza bruta, token manipulado —
   `CLAUDE.md §9`).

---

## 21. Estimación de complejidad por fase

| Fase | Complejidad | Motivo principal |
|---|---|---|
| 1 — Cimientos | Media | Código nuevo pero acotado y sin dependencias externas |
| 2 — Endpoints auth interno | Media | Reutiliza casi todo `SecurityConfig` existente |
| 3 — Endpoints auth Portal | Baja-Media | Réplica directa de la Fase 2 |
| 4 — Frontend | Media | Lógica PKCE ya existe; solo cambian endpoints + nuevas pantallas |
| 5 — Recuperación de contraseña | Alta | Depende de una capacidad no existente (envío de email) |
| 6 — Migración de usuarios | Alta | Impacto operativo/de soporte, no solo técnico; coordinación con usuarios reales del Portal Cliente (clientes externos, no solo personal interno) |
| 7 — Retirada de Keycloak | Baja | Mecánica, sin lógica de negocio nueva |
| 8 — Endurecimiento | Media-Alta | Superficie de seguridad nueva sin rodaje (§14) exige tests exhaustivos, no solo funcionales |

---

## 22. Criterios de aceptación para la futura implementación (propuesta)

- Ningún endpoint de auth expone contraseñas ni hashes en logs, respuestas de error, ni trazas.
- Todos los tests cross-tenant y de acceso no autorizado exigidos por `CLAUDE.md §9` existen y
  pasan, incluyendo: token expirado, token con firma inválida, token de un realm/flujo usado contra
  el filtro del otro (Portal vs. interno), fuerza bruta sobre login.
- El contrato stateless se mantiene: `allowCredentials(false)`, ningún estado de sesión en el
  servidor salvo la tabla de refresh tokens (revocable, no una sesión de framework).
- La separación Portal Cliente/interno se preserva verificablemente (ADR-PORTAL-AUTH-001 no se
  debilita — ningún código compartido entre ambos emisores de tokens salvo utilidades genéricas de
  hashing/firmado sin estado de negocio).
- `mvn verify` y suite de frontend en verde; sin regresión en ningún test existente.
- Documentación actualizada: `19_IDENTITY_OAUTH_SPECIFICATION.md` (ya agnóstico, revisar si sigue
  siendo preciso), nuevo ADR en `12_DECISION_LOG.md` documentando la decisión y su justificación,
  `GETTING_STARTED.md` actualizado con el nuevo flujo de arranque (sin Keycloak).

---

## 23. Riesgos, bloqueantes y preguntas abiertas

**Riesgos:**
- Superficie de seguridad nueva sin historial de auditoría externa (§14) — mitigable con tests
  exhaustivos pero no eliminable por diseño.
- Migración de usuarios con impacto en clientes externos del Portal (no solo personal interno) —
  riesgo reputacional/de soporte si se ejecuta sin comunicación adecuada.
- `external_identity_id` sin constraint UNIQUE (deuda ya señalada en ADR-IDENTITY-001, sin
  resolver) — se convierte en más crítico si pasa a ser la clave de identidad de un sistema propio.

**Bloqueantes:**
- No existe hoy ningún mecanismo de envío de email en el inventario auditado — bloqueante directo
  de la Fase 5 (recuperación de contraseña) y recomendable-pero-no-estrictamente-bloqueante de la
  Fase 6 (migración, si se opta por el flujo proactivo).

**Preguntas abiertas (para el usuario, no resueltas por este análisis):**
1. ¿Cuál es el motivo de negocio que motiva sustituir Keycloak (coste operativo, simplicidad de
   despliegue, control total, u otro)? Condiciona si la opción D (§16) debería reconsiderarse como
   alternativa válida de menor esfuerzo.
2. ¿Existe ya, o se prevé, un proveedor de envío de email para Brika (transaccional) que deba
   reutilizarse, o hay que seleccionarlo como parte de este trabajo?
3. ¿Es aceptable exigir restablecimiento de contraseña a todos los usuarios existentes (incluidos
   clientes del Portal) en el corte, o se requiere explorar alternativas de migración asistida
   (p. ej. ventana de convivencia con ambos sistemas)?
4. ¿Hay requisito de MFA a corto/medio plazo que deba influir en el diseño de Fase 1, aunque se
   implemente después?
5. ¿Qué ocurre con el tema de login Keycloak personalizado ya construido (`keycloak/themes/
   brikka/`, sprint anterior interrumpido y no verificado) — se descarta directamente al adoptar
   esta arquitectura, o se prefiere primero cerrar y verificar ese trabajo aunque quede obsoleto
   poco después?

---

## 24. Decisiones que requieren autorización explícita del usuario

1. **Arquitectura a adoptar**: confirmar la opción A (§17) frente a las alternativas B/C/D, o
   solicitar una combinación/variante distinta.
2. **Aceptar el flujo de restablecimiento obligatorio de contraseña** para todos los usuarios
   existentes como estrategia de migración (§19), incluyendo el impacto en clientes externos del
   Portal Cliente.
3. **Seleccionar/aprobar un proveedor de envío de email** transaccional, prerequisito de la Fase 5
   y potencialmente de la Fase 6.
4. **Orden y alcance de las fases** (§20): en particular, si la Fase 6 (migración real de usuarios)
   se ejecuta en el mismo sprint que las Fases 1-4, o se trata como un sprint propio posterior con
   su propia ventana de comunicación a usuarios.
5. **Destino del trabajo ya invertido en el tema de login Keycloak personalizado** (§23, pregunta
   5) — mantener parado, descartar, o cerrar/verificar antes de continuar.
6. **Política de retención de los archivos de Keycloak retirados** (`keycloak/*.json`, tema de
   login) tras la Fase 7 — archivar vs. eliminar, conforme a `CLAUDE.md §3` ("no eliminar
   versiones históricas sin política explícita").

---

## CIERRE DEL SPRINT 22

Esta fase de análisis ha finalizado. **No se ha implementado ninguna parte del diseño descrito en
este documento.** Ningún archivo de código, configuración, Docker, base de datos, ni ningún commit
se ha modificado durante la elaboración de este informe.

**Decisiones que requieren tu autorización explícita antes de continuar:** ver lista completa en
§24 (arquitectura a adoptar, estrategia de migración de contraseñas, proveedor de email, alcance
por fases, destino del tema de login Keycloak, y política de retención de archivos retirados).

**Recomendación técnica**: adoptar la arquitectura A — autenticación propia stateless con JWT
firmado por Brika (§17) — por ser la opción de menor radio de impacto sobre el `SecurityConfig`,
converters, `TenantContext` y `AuthorizationService` ya existentes, y la que mejor preserva tanto
el contrato stateless aprobado como la frontera de seguridad del Portal Cliente.

**Quedo a la espera de tu autorización explícita y clara para continuar con cualquier
implementación.** No interpretaré silencio, una respuesta ambigua, ni ningún comentario informal
como autorización — solo procederé ante una confirmación explícita.
