# BRIKA — IDENTITY & OAUTH/OIDC SPECIFICATION V1

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
