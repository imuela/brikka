# BRIKA — DOCUMENTATION STATUS

Este documento es la **única fuente del estado documental vigente** de Brika (`ADR-PROCESS-003`). Para el índice de documentos, ver `README.md`. Para el histórico de decisiones, ver `12_DECISION_LOG.md`.

## Estado actual

DOCUMENTACIÓN V1 CONSOLIDADA. SEGUNDA AUDITORÍA DE CONSISTENCIA COMPLETADA.

Las inconsistencias detectadas en la primera revisión cruzada (`11_CROSS_DOCUMENT_REVIEW.md`) y en la auditoría posterior fueron resueltas mediante 11 ADR (`ADR-DOC-001`, `ADR-PLATFORM-001`, `ADR-INTEGRATIONS-001`, `ADR-AUDIT-001`, `ADR-COMMS-001`, `ADR-COMMS-002`, `ADR-NOTIF-001`, `ADR-PROCESS-001/002/003`, `ADR-AI-001`), aplicadas a toda la documentación afectada.

## Bloques cerrados

- producto;
- funcional;
- dominio (incluye entidades de plataforma, actividad, adjuntos y participantes de conversación);
- workflows;
- permisos (incluye permisos de planes, requirements, actividad y participantes);
- Portal Cliente (regla `tenant + case + participant + visibility`);
- scoring;
- bancos/contactos;
- ERD (congelado, incluye entidades de la segunda auditoría);
- PostgreSQL (congelado, coincide con el ERD);
- API (incluye endpoints de document-requirements, plans/subscriptions, activities, message attachments, conversation participants);
- storage (incluye adjuntos de mensaje);
- OAuth/OIDC;
- RabbitMQ (incluye notificaciones por canal y flujo del Python Worker);
- IA V1 (incluye aislamiento del Python Worker respecto a PostgreSQL);
- cloud;
- testing;
- roadmap/sprints unificados en un único documento de ejecución (`25_CLAUDE_CODE_EXECUTION_GUIDE.md`);
- guía Claude Code.

## Pendientes después de aprobación

Son decisiones de implementación/configuración, no de diseño conceptual:
- proveedor cloud concreto;
- proveedor OIDC concreto;
- proveedor de Object Storage concreto;
- proveedor/modelos IA;
- valores RPO/RTO;
- secretos y dominios;
- configuración de infraestructura;
- facturación/pago automático de suscripciones (fuera de V1);
- proveedores concretos de `PUSH`/`SMS` (fuera de V1);
- integraciones externas concretas (fuera de V1, solo scaffolding mínimo).

## Estado de programación

NO INICIADA. Sprint 0 no debe comenzar sin aprobación explícita tras revisar el informe de cierre.
