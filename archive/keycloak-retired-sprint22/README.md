# Keycloak — retirado (Sprint 22 cierre)

Este directorio contiene la configuración de Keycloak (realm exports `brika-realm.json` y
`brika-portal-realm.json`, y el tema de login personalizado `themes/brikka/login/`) que estuvo
activa como proveedor de identidad de Brika hasta el Sprint 22.

**Estado**: retirado del entorno local. Brika usa autenticación propia (JWT autofirmados,
Argon2id) — ver `12_DECISION_LOG.md` ADR-AUTH-001 y `27_KEYCLOAK_REMOVAL_ANALYSIS.md` para el
análisis y la decisión completos.

Archivado (no eliminado) conforme a `CLAUDE.md` §3 — el trabajo de diseño (tema de login) y los
realm exports quedan disponibles como referencia histórica, pero ya no se montan en ningún
servicio Docker ni son necesarios para ejecutar Brika en local.
