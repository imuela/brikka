-- Sprint 22 (autenticación propia, Fase 1): infraestructura de credenciales locales, independiente
-- de Keycloak, que convive con él (Keycloak no se retira en este sprint — ver 27_KEYCLOAK_REMOVAL_
-- ANALYSIS.md §10 de la autorización). Tablas separadas para usuarios internos y Portal Cliente,
-- reflejando deliberadamente la misma frontera física que ADR-PORTAL-AUTH-001 ya exige entre ambos
-- dominios: ninguna tabla ni columna se comparte entre los dos lados.

-- =========================================================================
-- Contraseñas (Argon2id) — nunca se almacena texto plano ni en logs.
-- =========================================================================

CREATE TABLE user_credentials (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    password_hash varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE portal_account_credentials (
    portal_account_id uuid PRIMARY KEY REFERENCES client_portal_accounts (id) ON DELETE CASCADE,
    password_hash varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

-- =========================================================================
-- Refresh tokens (opacos, hash SHA-256 almacenado — nunca el valor en claro).
-- family_id identifica la cadena de rotación: si un token ya usado/revocado se
-- presenta de nuevo, toda la familia se revoca (detección de reutilización).
-- =========================================================================

CREATE TABLE user_refresh_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id uuid NOT NULL,
    token_hash varchar(255) NOT NULL,
    issued_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    replaced_by_token_id uuid REFERENCES user_refresh_tokens (id)
);
CREATE UNIQUE INDEX uq_user_refresh_tokens_hash ON user_refresh_tokens (token_hash);
CREATE INDEX idx_user_refresh_tokens_user_id ON user_refresh_tokens (user_id);
CREATE INDEX idx_user_refresh_tokens_family_id ON user_refresh_tokens (family_id);

CREATE TABLE portal_refresh_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    portal_account_id uuid NOT NULL REFERENCES client_portal_accounts (id) ON DELETE CASCADE,
    family_id uuid NOT NULL,
    token_hash varchar(255) NOT NULL,
    issued_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    replaced_by_token_id uuid REFERENCES portal_refresh_tokens (id)
);
CREATE UNIQUE INDEX uq_portal_refresh_tokens_hash ON portal_refresh_tokens (token_hash);
CREATE INDEX idx_portal_refresh_tokens_account_id ON portal_refresh_tokens (portal_account_id);
CREATE INDEX idx_portal_refresh_tokens_family_id ON portal_refresh_tokens (family_id);

-- =========================================================================
-- Tokens de recuperación de contraseña (infraestructura — Fase 5). Uso único,
-- expiración corta; el mecanismo de envío de email queda fuera de este sprint
-- (autorización §8: no se puede elegir proveedor externo sin autorización).
-- =========================================================================

CREATE TABLE user_password_reset_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at timestamptz
);
CREATE UNIQUE INDEX uq_user_password_reset_tokens_hash ON user_password_reset_tokens (token_hash);
CREATE INDEX idx_user_password_reset_tokens_user_id ON user_password_reset_tokens (user_id);

CREATE TABLE portal_password_reset_tokens (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    portal_account_id uuid NOT NULL REFERENCES client_portal_accounts (id) ON DELETE CASCADE,
    token_hash varchar(255) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL,
    used_at timestamptz
);
CREATE UNIQUE INDEX uq_portal_password_reset_tokens_hash
    ON portal_password_reset_tokens (token_hash);
CREATE INDEX idx_portal_password_reset_tokens_account_id
    ON portal_password_reset_tokens (portal_account_id);

-- =========================================================================
-- Protección contra fuerza bruta / credential stuffing / password spraying
-- (autorización §11 "Login protection"). Conteo de intentos fallidos por
-- identidad, ventana simple basada en timestamps — sin infraestructura nueva
-- (Redis, etc.), coherente con "no introduzcas infraestructura adicional sin
-- autorización" (§16).
-- =========================================================================

CREATE TABLE login_attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    realm varchar(20) NOT NULL,
    identifier varchar(255) NOT NULL,
    succeeded boolean NOT NULL,
    attempted_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_login_attempts_realm_identifier_time
    ON login_attempts (realm, identifier, attempted_at);
