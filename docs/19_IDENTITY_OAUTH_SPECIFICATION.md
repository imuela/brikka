# BRIKA — IDENTITY & OAUTH/OIDC SPECIFICATION V1

> **Nota de estado (cierre Sprint 22):** este documento describe el diseño original (proveedor OIDC
> externo con Authorization Code + PKCE), que quedó **superado por la decisión de autenticación
> propia** (`12_DECISION_LOG.md` ADR-AUTH-001): desde Sprint 22 Brika emite y valida sus propios JWT
> (RS256), almacena contraseñas Argon2id y usa refresh tokens opacos con rotación y detección de
> reutilización. No existe ningún proveedor OIDC externo (Keycloak fue retirado). Se conserva como
> documento histórico del diseño de identidad original. La implementación real está documentada en
> `12_DECISION_LOG.md` (ADR-AUTH-001 y su adenda de cierre), `GETTING_STARTED.md` §5 y
> `06_SECURITY_SPECIFICATION.md`.

## 1. Principio

Brika delegará autenticación a un proveedor OIDC compatible.

Spring Security validará tokens.

## 2. Usuarios internos

Claims mínimos:
- subject;
- email;
- name;
- tenant/company mapping;
- roles/claims necesarios.

La autorización fina permanecerá en Brika.

## 3. Portal Cliente

El cliente tendrá identidad separada lógicamente de usuarios internos.

Nunca se concederán permisos internos por reutilizar una cuenta.

## 4. Login

Frontend:
- Authorization Code + PKCE.

Backend:
- validación de issuer;
- audience;
- signature;
- expiration;
- scopes/claims.

## 5. Sesiones

No guardar secretos en localStorage.

Preferir patrón seguro de cookies cuando la arquitectura lo permita.

## 6. Logout

Revocar/invalidar sesión según capacidades del proveedor.

## 7. Tenant

El `company_id` nunca debe ser aceptado como claim libre enviado por el navegador.

El backend debe resolver la pertenencia del usuario a la empresa desde la identidad registrada.

## 8. MFA

Debe poder habilitarse desde el proveedor de identidad.

## 9. Cambio de proveedor

La aplicación debe evitar acoplar el dominio a APIs propietarias del proveedor.
