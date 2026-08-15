# BRIKA — REQUIREMENTS TRACEABILITY V1

## 1. Objetivo

Garantizar que lo definido durante el diseño no se pierda al pasar a implementación.

## 2. Requisitos críticos trazados

| ID | Requisito | Documento |
|---|---|---|
| BRK-001 | SaaS multiempresa | Master / Technical / Security |
| BRK-002 | Tenant isolation | Technical / Security |
| BRK-003 | Roles SUPERADMIN/MANAGER/BROKER/CLIENT | Functional / Permissions |
| BRK-004 | Portal Cliente independiente | Master / Portal |
| BRK-005 | Visibilidad publicada explícitamente | Portal / Security |
| BRK-006 | Operación como eje de negocio | Product / Domain |
| BRK-007 | Documentación versionada | Functional / Domain |
| BRK-008 | TASK distinto de DOCUMENT_REQUEST | Domain / Workflow |
| BRK-009 | Scoring explicable | Scoring |
| BRK-010 | Motor bancario determinista | Bank Engine |
| BRK-011 | Overrides versionados y auditados | Bank Engine |
| BRK-012 | Procedencia de datos | Data Governance |
| BRK-013 | AI Gateway | Technical / AI |
| BRK-014 | IA sin acceso directo a BD | AI |
| BRK-015 | Auditoría | Security / Testing |
| BRK-016 | Angular + Spring Boot + PostgreSQL | Technical |
| BRK-017 | Flyway | Technical / Database |
| BRK-018 | Docker + CI/CD | DevOps |
| BRK-019 | Tests de tenant isolation | Testing |
| BRK-020 | Claude Code debe respetar documentación | CLAUDE.md |

## 3. Regla

Antes de congelar V1, todo requisito crítico debe tener:
- especificación;
- criterio de aceptación;
- ubicación documental;
- plan de implementación.


| BRK-021 | Catálogo BANK global + BANK_CONTACT propiedad de COMPANY | Bank Engine / Domain / Permissions |
| BRK-022 | Los contactos bancarios de una empresa son invisibles para otras empresas | Security / Bank Engine |
| BRK-023 | Una empresa puede tener múltiples contactos por banco | Bank Engine / Domain |
| BRK-024 | Las solicitudes bancarias conservan el contacto utilizado históricamente | Bank Engine / Database |

| BRK-025 | ERD conceptual definitivo | 15_DEFINITIVE_ERD.md |

| BRK-026 | Catálogo de requisitos documentales condicionado (`document_requirements`) | ADR-DOC-001 / Domain / ERD / Database |
| BRK-027 | `File` absorbido en `document_versions`, sin tabla propia | ADR-DOC-001 / Domain / ERD |
| BRK-028 | Gestión de planes, entitlements y suscripciones de empresa | ADR-PLATFORM-001 / Database / Permissions |
| BRK-029 | Autorización de funcionalidades limitadas por plan = permission + entitlement | ADR-PLATFORM-001 / Security / API |
| BRK-030 | Integraciones como scaffolding mínimo, sin proveedores concretos en V1 | ADR-INTEGRATIONS-001 / Database / API |
| BRK-031 | `activities` distinta de `audit_events` | ADR-AUDIT-001 / Domain / Database |
| BRK-032 | Adjuntos de mensaje (`message_attachments`) | ADR-COMMS-001 / Database / Storage |
| BRK-033 | Participantes de conversación obligatorios en tipo CLIENT (`conversation_participants`) | ADR-COMMS-002 / Security / Portal Cliente |
| BRK-034 | Notificaciones V1 limitadas a IN_APP/EMAIL, con `notification_deliveries` por canal | ADR-NOTIF-001 / Database / RabbitMQ |
| BRK-035 | Roadmap único de ejecución (`25_CLAUDE_CODE_EXECUTION_GUIDE.md`) | ADR-PROCESS-001 |
| BRK-036 | Sprint 0 = infraestructura únicamente, sin esquema ni lógica de negocio | ADR-PROCESS-002 |
| BRK-037 | Python Worker sin acceso directo a PostgreSQL, aislado de red | ADR-AI-001 / Security / Technical |
