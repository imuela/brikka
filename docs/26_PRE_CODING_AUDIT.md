# BRIKA V1 — AUDITORÍA PRE-CODIFICACIÓN

> **Nota de actualización:** las inconsistencias señaladas en este documento (PostgreSQL como especificación conceptual, `BANK_CONTACT` sin regla de duplicados, alcance de Sprint 0, etc.) han sido resueltas mediante los ADR de la segunda auditoría documental (`12_DECISION_LOG.md`, sección "Cierre de arquitectura y documentación"). El alcance de Sprint 0 descrito en la sección 5 de este documento se mantiene como referencia histórica; el alcance **vigente y autoritativo** de Sprint 0 es el definido en `ADR-PROCESS-002` y `25_CLAUDE_CODE_EXECUTION_GUIDE.md` §3 (infraestructura únicamente, sin esquema ni migraciones de negocio).

## Veredicto

**APTO PARA PASAR A SPRINT 0, PERO NO PARA EMPEZAR A IMPLEMENTAR NEGOCIO TODAVÍA.**

La arquitectura general es coherente y el modelo multiempresa está bien planteado. Sin embargo, antes de congelar el ZIP como "fuente de verdad", conviene cerrar varias precisiones técnicas.

## 1. Puntos correctamente cerrados

- SaaS multiempresa.
- `BANK` global.
- `BANK_CONTACT` propiedad de `COMPANY`.
- aislamiento de contactos bancarios entre empresas.
- Portal Cliente separado mediante permisos.
- documentos versionados.
- publicación explícita de documentos al Portal Cliente.
- workflow + historial.
- RBAC.
- API versionada.
- PostgreSQL como base de datos.
- RabbitMQ para procesos asíncronos.
- IA como asistente y no como autoridad de negocio.
- guía de ejecución para Claude Code.
- estrategia de tests de aislamiento de tenant.

## 2. Correcciones recomendadas antes de Sprint 1

### A. PostgreSQL debe pasar de especificación conceptual a constraints ejecutables

La especificación actual describe PK/FK/índices, pero todavía no congela todos los `NOT NULL`, `CHECK`, `ON DELETE`, unicidades y RLS.

**Acción:** Sprint 0 prepara la infraestructura; Sprint 1 debe crear una migración revisada antes de desarrollar módulos de negocio.

### B. `BANK_CONTACT` necesita una regla explícita de duplicados

Una empresa puede tener varios contactos del mismo banco, por lo que NO debe existir `UNIQUE(company_id, bank_id)`.

Sí debe existir una estrategia para evitar duplicados accidentales, por ejemplo mediante combinación de email/teléfono/nombre o deduplicación de aplicación.

**Decisión:** no imponer unicidad por banco.

### C. `BANK_REQUEST.contact_snapshot`

Correcto conservar snapshot histórico. Debe quedar claro que el snapshot es de auditoría/histórico y que el `bank_contact_id` sigue siendo la referencia al recurso original cuando exista.

### D. Portal Cliente

La autorización debe estar basada en:
- identidad del cliente;
- `client_portal_account`;
- relación con `case`;
- publicación del recurso.

Nunca por conocer un UUID.

### E. Identidad

El proveedor OIDC es una decisión pendiente. No debe bloquear Sprint 0: se puede levantar un proveedor OIDC local para desarrollo.

### F. Cloud

No elegir todavía proveedor de producción. Primero entorno local reproducible. RPO/RTO se fijarán al seleccionar infraestructura/SLA.

## 3. Riesgos que NO deben pasar a código

1. Crear endpoints sin tenant check.
2. Permitir `companyId` arbitrario desde frontend.
3. Exponer `BANK_CONTACT` por `bankId` sin tenant.
4. Hacer documentos internos visibles automáticamente en Portal Cliente.
5. Permitir que IA consulte SQL directamente.
6. Hacer matching bancario no reproducible.
7. Modificar migraciones ya aplicadas.
8. Guardar secretos en Git.
9. Crear microservicios innecesarios en V1.
10. Introducir lógica de negocio en controllers.

## 4. Estado de preparación

### Arquitectura
**VERDE**

### Modelo de dominio
**VERDE**

### Seguridad conceptual
**VERDE/ÁMBAR** — falta concretar RLS y constraints físicos.

### Base de datos
**ÁMBAR** — lista para implementación, pero no congelada al nivel SQL final.

### API
**VERDE/ÁMBAR** — endpoints definidos; faltan schemas OpenAPI completos.

### Infraestructura local
**VERDE** para Sprint 0.

### Producción
**NO CONGELADA**, correctamente. No debe desplegarse todavía.

## 5. Siguiente paso recomendado

No empezar por servidores de producción.

Ejecutar **Sprint 0 — Developer Environment Foundation**:

1. crear repositorio;
2. copiar documentación a `/docs`;
3. crear estructura Angular + Spring Boot;
4. Docker Compose;
5. PostgreSQL;
6. RabbitMQ;
7. Object Storage S3-compatible local;
8. OIDC local;
9. Flyway;
10. variables `.env.example`;
11. health checks;
12. tests mínimos;
13. CI;
14. README de arranque.

Después de validar Sprint 0, comenzar Sprint 1 con la migración PostgreSQL y el núcleo de identidad/tenant/RBAC.

## 6. Regla para Claude Code

Claude Code debe leer primero toda la documentación y ejecutar una fase de **DOCUMENTATION AUDIT**.

Si detecta contradicción, no debe decidir por su cuenta.

Debe reportar:
- documento;
- conflicto;
- impacto;
- propuesta;
- decisión requerida.

