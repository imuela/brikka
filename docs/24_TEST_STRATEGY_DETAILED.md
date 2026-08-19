# BRIKA — TEST STRATEGY DETAILED V1

## 1. Pirámide

- unit;
- integration;
- API;
- security;
- E2E;
- smoke;
- performance en áreas críticas.

## 2. Tests obligatorios de tenant isolation

Casos mínimos:
- usuario A no lee CASE de empresa B;
- usuario A no lee BANK_CONTACT de empresa B;
- usuario A no descarga DOCUMENT de empresa B;
- usuario A no accede a CONVERSATION de empresa B;
- Portal Cliente A no accede al cliente B.

## 3. Tests de permisos

Cada endpoint sensible debe tener:
- autorizado;
- no autenticado;
- autenticado sin permiso;
- recurso de otro tenant.

## 4. Workflow

Probar todas las transiciones permitidas y prohibidas.

## 5. Documentos

- versionado;
- checksum;
- publicación;
- revocación;
- descarga autorizada.

## 6. Banco

- múltiples contactos por banco;
- aislamiento de contactos;
- snapshot histórico;
- ofertas;
- selección.

## 7. IA

- tool authorization;
- minimización;
- auditoría;
- validación humana.

## 8. API

Contract tests derivados de OpenAPI.

## 9. CI

No permitir merge si fallan:
- tests;
- análisis estático;
- checks críticos de seguridad.
