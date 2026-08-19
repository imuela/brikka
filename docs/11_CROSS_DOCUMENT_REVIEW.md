# BRIKA — CROSS-DOCUMENT REVIEW V1

## Objetivo

Revisión de consistencia entre los documentos de producto, funcionales, dominio, arquitectura, seguridad, Portal Cliente, scoring, IA, DevOps, testing, decisiones y planificación.

## Resultado ejecutivo

La documentación presenta una línea arquitectónica y funcional coherente para V1:

- SaaS multiempresa.
- Angular + Spring Boot + PostgreSQL.
- Flyway.
- Arquitectura modular monolith.
- Portal Cliente independiente y controlado por el broker.
- SUPERADMIN / MANAGER / BROKER / CLIENT.
- Aislamiento obligatorio por tenant.
- Documentación versionada.
- Scoring explicable.
- Motor bancario determinista y versionado.
- AI Gateway/Orchestrator.
- IA sin acceso directo a la base de datos.
- RabbitMQ para procesos/eventos asíncronos cuando proceda.
- Auditoría de acciones sensibles.

## Puntos que quedan abiertos y deben cerrarse antes del desarrollo

### CR-001 — Catálogo definitivo de estados — CERRADO

La documentación define el flujo conceptual, pero todavía hay que congelar:
- códigos técnicos;
- nombres visibles;
- transiciones permitidas;
- transiciones manuales;
- transiciones automáticas;
- estados terminales.

### CR-002 — Catálogo definitivo de permisos — CERRADO

La matriz funcional existe, pero falta convertirla en:
- permission codes;
- recursos;
- acciones;
- alcance;
- restricciones por tenant;
- restricciones por relación con CASE.

### CR-003 — ERD definitivo

El modelo conceptual está definido, pero falta convertirlo en un ERD con cardinalidades, claves y restricciones.

### CR-004 — PostgreSQL definitivo

Falta especificar:
- columnas;
- tipos;
- constraints;
- índices;
- FK;
- índices únicos;
- estrategia exacta de tenant isolation;
- RLS si se confirma.

### CR-005 — API definitiva

La API está definida a nivel de recursos, pero falta congelar:
- endpoints;
- request/response;
- DTOs;
- códigos de error;
- paginación;
- filtros;
- ordenación;
- idempotencia.

### CR-006 — Storage documental

Falta congelar:
- proveedor inicial;
- bucket/container;
- estructura lógica;
- lifecycle;
- retención;
- antivirus;
- generación de URLs temporales.

### CR-007 — Identidad

OAuth/OIDC está definido como dirección arquitectónica. Falta elegir proveedor inicial y flujo exacto para usuarios internos y Portal Cliente.

### CR-008 — Mensajería

RabbitMQ está definido como componente disponible. Falta cerrar:
- exchanges;
- queues;
- routing keys;
- retry;
- dead-letter;
- idempotencia.

### CR-009 — IA

Está definida la arquitectura y el principio de seguridad. Falta congelar los casos de uso V1 que realmente entran en el primer release.

### CR-010 — Bancos

El motor conceptual está definido. Falta congelar el modelo de criterios, productos, versiones y reglas de compatibilidad.

## Reglas de congelación

Antes de generar el ZIP final para Claude Code:

1. Resolver CR-001 a CR-010.
2. Crear ERD.
3. Crear modelo PostgreSQL.
4. Crear API detallada.
5. Revisar permisos contra API.
6. Revisar Portal Cliente contra permisos.
7. Revisar aceptación contra workflows.
8. Actualizar DECISION_LOG.
9. Ejecutar segunda revisión cruzada.
10. Generar ZIP final.

## Resultado

**Estado: DOCUMENTACIÓN BASE CONSOLIDADA — NO CONGELADA PARA IMPLEMENTACIÓN.**

No se detecta una contradicción arquitectónica que obligue a rehacer el diseño. Los siguientes pasos son de precisión y congelación del contrato.

## Actualización

CR-001 y CR-002 han sido congelados mediante `13_DEFINITIVE_WORKFLOW_SPECIFICATION.md` y `14_DEFINITIVE_PERMISSION_CATALOG.md`.


## Actualización — BANK_CONTACT

La decisión sobre contactos bancarios queda cerrada:

- `BANK` = catálogo global.
- `BANK_CONTACT` = recurso propiedad de `COMPANY`.
- Una empresa puede tener N contactos para un mismo banco.
- El broker no es propietario del contacto.
- El contacto se filtra por tenant.
- La selección de contacto en una solicitud bancaria debe conservar trazabilidad histórica.

Este requisito deberá reflejarse en el ERD y en el modelo PostgreSQL definitivo.

## Actualización — ERD

El ERD conceptual definitivo queda cerrado en `15_DEFINITIVE_ERD.md`. El siguiente paso es transformar este modelo conceptual en el esquema físico PostgreSQL.
