# BRIKA — MASTER SPECIFICATION V1

**Documento:** BRIKA_MASTER_SPEC.md  
**Versión:** 1.0  
**Estado:** En consolidación  
**Proyecto:** Brika  
**Ámbito:** SaaS multiempresa para brokers hipotecarios

---

## 1. Identidad del proyecto

Brika es un SaaS multiempresa orientado inicialmente a brokers hipotecarios, diseñado para centralizar y digitalizar la gestión integral de operaciones de financiación hipotecaria.

La plataforma permitirá gestionar:

- empresas;
- usuarios;
- clientes;
- operaciones hipotecarias;
- inmuebles;
- documentación;
- solicitudes de financiación;
- entidades bancarias;
- ofertas;
- tareas;
- comunicaciones;
- scoring;
- reporting;
- auditoría;
- automatizaciones;
- inteligencia artificial.

Brika se diseñará desde el inicio con una arquitectura preparada para una futura adaptación a otros sectores, especialmente inmobiliarias, sin comprometer el dominio hipotecario de V1.

---

## 2. Objetivo principal

El objetivo de Brika es proporcionar al broker una plataforma única para gestionar una operación hipotecaria desde la captación del cliente hasta su resolución y formalización.

Flujo conceptual:

CAPTACIÓN
→ CLIENTE
→ PREESTUDIO
→ OPERACIÓN
→ DOCUMENTACIÓN
→ ANÁLISIS
→ SCORING
→ BÚSQUEDA DE FINANCIACIÓN
→ ENVÍO A BANCOS
→ OFERTAS
→ SELECCIÓN
→ FORMALIZACIÓN
→ CIERRE

---

## 3. Problema que resuelve

Brika pretende solucionar la fragmentación habitual de la gestión hipotecaria.

Actualmente un broker puede necesitar simultáneamente:

- Excel
- Email
- WhatsApp
- almacenamiento de archivos
- CRM
- notas
- portales bancarios
- calendarios
- documentos PDF

Brika centralizará estos procesos en una única plataforma.

---

## 4. Usuarios

### 4.1 SUPERADMIN

Administrador de la plataforma Brika.

Gestiona:

- empresas;
- planes;
- funcionalidades;
- configuración global;
- catálogos;
- soporte;
- administración de plataforma.

No pertenece necesariamente a una empresa concreta.

### 4.2 MANAGER

Administrador de una empresa cliente de Brika.

Puede gestionar:

- usuarios;
- brokers;
- clientes;
- operaciones;
- configuración de empresa;
- documentación;
- reporting;
- Portal Cliente;
- permisos.

### 4.3 BROKER

Usuario operativo.

Gestiona:

- clientes;
- operaciones;
- documentación;
- financiación;
- bancos;
- tareas;
- comunicaciones;
- seguimiento.

### 4.4 CLIENT

Cliente final.

No será tratado como un usuario interno tradicional.

Dispondrá de un Portal Cliente independiente, con autenticación y permisos propios.

---

## 5. Arquitectura multiempresa

Brika será un sistema multi-tenant.

Conceptualmente:

BRIKA
- COMPANY A
  - Users
  - Clients
  - Cases
- COMPANY B
  - Users
  - Clients
  - Cases
- COMPANY C
  - Users
  - Clients
  - Cases

Una empresa nunca podrá acceder a los datos de otra.

La seguridad de tenant será una regla estructural.

---

## 6. Portal Cliente

El Portal Cliente es una parte oficial de Brika V1.

No será simplemente una pantalla adicional del backend interno.

Su función será proporcionar al cliente final acceso controlado a la información que el broker decida publicar.

### Funcionalidades

#### Operaciones

El cliente podrá consultar:

- operación;
- estado publicado;
- información autorizada;
- progreso.

#### Documentación

Podrá:

- consultar documentos visibles;
- recibir solicitudes;
- subir documentación;
- sustituir documentos rechazados;
- consultar el estado de revisión.

#### Comunicación

Podrá:

- recibir mensajes;
- responder;
- recibir notificaciones.

#### Datos

Podrá actualizar determinados datos que el broker permita modificar.

---

## 7. Principio fundamental del Portal Cliente

La información interna del broker no es automáticamente visible al cliente.

Debe existir una separación entre:

- información interna;
- información publicada al cliente.

El broker tendrá control granular sobre aquello que se publica.

---

## 8. Operación hipotecaria

La entidad central de negocio será `CASE`.

Una operación podrá contener:

- CLIENTS
- PROPERTY
- FINANCING
- DOCUMENTS
- TASKS
- COMMUNICATIONS
- SCORING
- BANK REQUESTS
- AUDIT

---

## 9. Clientes

Un cliente podrá participar en diferentes operaciones de su empresa.

Roles previstos:

- HOLDER
- CO_HOLDER
- GUARANTOR
- OTHER

Esto permitirá soportar:

- operaciones individuales;
- operaciones con dos titulares;
- avalistas;
- configuraciones futuras.

---

## 10. Documentación

El sistema utilizará un modelo documental versionado:

DOCUMENT TYPE
→ REQUIREMENT
→ REQUEST
→ DOCUMENT
→ VERSION
→ FILE

Nunca se sobrescribirá silenciosamente una versión anterior.

Debe conservarse:

- quién subió el documento;
- cuándo;
- versión;
- quién lo revisó;
- estado;
- motivo de rechazo;
- historial.

---

## 11. Financiación

Brika distinguirá:

FINANCING REQUEST
→ BANK REQUEST
→ BANK OFFER
→ FINAL FINANCING

Esto permitirá conservar la trazabilidad completa del proceso.

---

## 12. Scoring

Brika tendrá un sistema de scoring explicable.

Se contemplan:

- CLIENT SCORE
- PROPERTY SCORE
- OPERATION SCORE

El resultado podrá mostrar el desglose de factores positivos y negativos.

El scoring será una herramienta de apoyo y no sustituirá la decisión del broker ni la decisión final de la entidad financiera.

---

## 13. Inteligencia Artificial

La IA estará desacoplada mediante un AI Gateway.

Arquitectura:

BRIKA
→ AI APPLICATION SERVICE
→ AI GATEWAY
→ AI PROVIDER

Esto permitirá cambiar de proveedor sin rediseñar el dominio.

Debe registrarse el consumo relevante para controlar:

- tokens;
- coste;
- modelo;
- empresa;
- operación;
- usuario.

---

## 14. Auditoría

Las operaciones sensibles deberán quedar registradas.

Especialmente:

- autenticación;
- cambios de permisos;
- modificaciones de clientes;
- cambios de estado;
- documentos;
- descargas;
- aprobaciones;
- rechazos;
- exportaciones;
- IA;
- integraciones.

---

## 15. Arquitectura tecnológica

La arquitectura V1 queda definida inicialmente como:

### Frontend

Angular + TypeScript

### Backend

Java + Spring Boot

### API

REST API

### Base de datos

PostgreSQL

### Migraciones

Flyway

### Infraestructura

Docker y CI/CD

### Almacenamiento

Object Storage para ficheros de negocio.

### Componentes auxiliares

- observabilidad;
- jobs/background workers;
- integraciones externas (scaffolding mínimo en V1, sin proveedores concretos — `ADR-INTEGRATIONS-001`);
- AI Gateway;
- Worker Python especializado en OCR/extracción/procesamiento documental: stateless, sin acceso directo a PostgreSQL ni credenciales de PostgreSQL, aislado a nivel de red, invocable únicamente vía AI Gateway/Orchestrator y/o RabbitMQ (`ADR-AI-001`);
- pgvector como extensión de la instancia PostgreSQL principal, no como base de datos independiente, para casos de RAG expresamente aprobados.

---

## 16. Principios técnicos

Brika deberá cumplir:

### Seguridad por diseño

La seguridad no se añadirá al final.

### Multi-tenancy por diseño

El tenant forma parte de la arquitectura.

### Auditoría

Las acciones críticas son trazables.

### API-first

Frontend y backend estarán desacoplados.

### Modularidad

Los módulos de negocio estarán separados.

### Testabilidad

Toda funcionalidad importante tendrá pruebas.

### Evolución

V1 debe permitir evolucionar hacia nuevas verticales sin reescribir el núcleo.

---

## 17. Alcance V1

La V1 incluye como mínimo:

- SaaS multiempresa;
- usuarios;
- roles;
- permisos;
- clientes;
- operaciones;
- titulares;
- inmuebles;
- documentación;
- Portal Cliente;
- tareas;
- mensajería;
- notificaciones;
- financiación;
- bancos;
- ofertas;
- simulaciones;
- scoring;
- auditoría;
- reporting base;
- integraciones base;
- AI Gateway.

---

## 18. Fuera de V1

Todo aquello que no esté especificado expresamente se clasificará como:

- PENDIENTE, si requiere una decisión;
- V2, si queda fuera del alcance inicial.

No se incorporarán funcionalidades simplemente porque sean técnicamente posibles.

---

## 19. Regla de oro del proyecto

El código debe adaptarse a la especificación de Brika, no la especificación a lo que resulte más fácil programar.

Cuando una decisión técnica requiera modificar una regla funcional:

PROPOSAL
→ REVIEW
→ APPROVAL
→ DOCUMENTATION UPDATE
→ IMPLEMENTATION

---

## 20. Estado de las decisiones

### DECIDIDO

- SaaS multiempresa.
- Orientación inicial a brokers hipotecarios.
- Portal Cliente independiente.
- Rol CLIENT separado de usuarios internos.
- Backend Java/Spring Boot.
- Frontend Angular.
- PostgreSQL.
- Flyway.
- Docker/CI/CD.
- Documentación versionada.
- Scoring explicable.
- AI Gateway.
- Auditoría (`audit_events`) separada del timeline funcional de negocio (`activities`) — `ADR-AUDIT-001`.
- Tenant isolation.
- Worker Python stateless para OCR/extracción, sin acceso directo a PostgreSQL, aislado a nivel de red — `ADR-AI-001`.
- pgvector como extensión de PostgreSQL, no como base de datos independiente — `ADR-AI-001`.
- Gestión de planes, entitlements y suscripciones de empresa (`plans`/`entitlements`/`plan_entitlements`/`company_subscriptions`) — `ADR-PLATFORM-001`.
- Integraciones como scaffolding mínimo de extensibilidad (`integrations`), sin proveedores concretos ni `integration_events` en V1 — `ADR-INTEGRATIONS-001`.
- `25_CLAUDE_CODE_EXECUTION_GUIDE.md` como único documento autoritativo de ejecución sprint a sprint — `ADR-PROCESS-001`.

### EN CONSOLIDACIÓN

- Estrategia exacta de RLS.
- Detalle final de almacenamiento de archivos.

### PENDIENTE

- Facturación y pago automático de suscripciones (fuera de V1).
- Proveedores concretos de notificación `PUSH`/`SMS` (arquitectura preparada, sin proveedor conectado en V1 — `ADR-NOTIF-001`).
- Integraciones externas concretas (solo scaffolding en V1 — `ADR-INTEGRATIONS-001`).
- Embeddings/RAG más allá de un caso de uso expresamente aprobado.
- Cualquier otra decisión no documentada explícitamente en los documentos oficiales de Brika.

---

## 21. Documentación oficial del proyecto

Este documento forma parte del paquete oficial de especificación.

La documentación completa se organizará en el paquete descrito en el índice vivo `README.md`, que incluye —además de los 14 documentos fundacionales listados originalmente— los documentos definitivos de cierre de arquitectura (ERD, esquema PostgreSQL, API detallada, storage, identidad/OAuth, RabbitMQ, alcance IA V1, motor bancario detallado, cloud, estrategia de test detallada y guía de ejecución de Claude Code).

Ante cualquier duda sobre qué documentos existen y su estado, `README.md` es el índice vivo y `10_DOCUMENTATION_STATUS.md` es la fuente del estado documental vigente (`ADR-PROCESS-003`).

Estos documentos constituirán la fuente de verdad para la implementación de Brika V1.

---

## 22. Regla para agentes de desarrollo

Cualquier agente de programación, incluido Claude Code, deberá:

1. Leer esta documentación antes de modificar el proyecto.
2. Respetar las decisiones marcadas como DECIDIDO.
3. No modificar reglas de negocio silenciosamente.
4. No romper el aislamiento entre empresas.
5. No exponer información interna al Portal Cliente.
6. Mantener trazabilidad de operaciones sensibles.
7. Implementar pruebas junto con las funcionalidades.
8. Documentar cualquier decisión nueva.
9. No introducir tecnologías no aprobadas sin justificación.
10. Solicitar revisión cuando una implementación contradiga esta especificación.

---

**FIN DEL MASTER SPECIFICATION V1**
