# BRIKA — TECHNICAL SPECIFICATION V1

## 1. Arquitectura

Brika V1 será un **modular monolith package-by-feature**, con API-first y separación clara entre frontend, backend y servicios especializados.

Angular → REST API → Spring Boot → PostgreSQL

Componentes auxiliares:
- Object Storage;
- RabbitMQ;
- Worker Python especializado en IA/OCR/procesamiento documental (`ADR-AI-001`): **stateless, sin acceso directo a PostgreSQL ni credenciales de PostgreSQL, aislado a nivel de red**, invocable únicamente mediante AI Gateway/Orchestrator y/o eventos RabbitMQ. No es un segundo backend de propósito general;
- pgvector/embeddings, como extensión de la instancia PostgreSQL principal (no una base de datos vectorial separada), únicamente cuando un caso de uso RAG esté expresamente aprobado en el alcance V1;
- AI Gateway/Orchestrator;
- observabilidad;
- CI/CD.

La modularidad debe permitir separar servicios posteriormente sin obligar a introducir microservicios prematuramente.

## 2. Backend

- Java
- Spring Boot
- Spring Security
- OAuth/OIDC para identidad cuando corresponda
- PostgreSQL
- Flyway

Organización preferente por feature/dominio, evitando una arquitectura basada exclusivamente en carpetas técnicas globales.

## 3. Frontend

Angular + TypeScript.

Capas:
- core;
- auth;
- shared;
- features;
- guards;
- API clients;
- servicios de estado.

El frontend nunca será una autoridad de seguridad.

## 4. Multi-tenancy

Cada request autenticado tendrá un contexto de tenant.

El tenant se obtiene de la identidad y del contexto autorizado, no de un company_id confiado enviado por el cliente.

Se utilizará aislamiento por tenant en aplicación y se podrá reforzar con PostgreSQL Row-Level Security.

## 5. Seguridad

- OAuth/OIDC;
- Spring Security;
- RBAC;
- entitlements;
- autorización por recurso;
- tenant isolation;
- auditoría;
- gestión segura de secretos;
- rate limiting donde corresponda.

## 6. Documentos

Los archivos se almacenarán en Object Storage privado.

La BD almacenará metadatos y referencias.

El procesamiento documental puede utilizar OCR y servicios Python especializados.

## 7. Mensajería y eventos

RabbitMQ se utilizará para procesos asíncronos y eventos cuando aporte valor.

Ejemplos:
- notificaciones;
- emails;
- procesamiento documental;
- integraciones;
- jobs de IA.

## 8. IA

Arquitectura conceptual completa (`ADR-AI-001`):

Angular → Spring Boot → AI Gateway/Orchestrator → RabbitMQ → Python Worker → resultado → Spring Boot → PostgreSQL

Ni el proveedor de IA externo ni el Python Worker **tendrán acceso directo a la base de datos**. `Python Worker → PostgreSQL` queda explícitamente **PROHIBIDO**: el worker solo entrega resultados a Spring Boot (vía endpoint interno, fuera de `/api/v1` público), que los valida y persiste en `document_extractions`.

El acceso a información se realizará mediante herramientas/servicios autorizados que respeten tenant, permisos y minimización de datos.

Embeddings y búsqueda semántica podrán utilizar PostgreSQL + pgvector cuando exista un caso de uso aprobado, pero no se implementan en V1 por defecto.

## 9. Integraciones

Las integraciones externas estarán encapsuladas mediante adapters.

Las acciones externas sensibles requerirán autorización explícita según el caso.

## 10. Observabilidad

- logs estructurados;
- correlation/request ID;
- métricas;
- health checks;
- trazas;
- alertas.

## 11. DevOps

Docker + CI/CD.

Entornos:
- local;
- development;
- staging;
- production.

## 12. Principios

- modularidad;
- seguridad por diseño;
- tenant isolation;
- API-first;
- testabilidad;
- trazabilidad;
- mínimo acoplamiento;
- evolución controlada.
