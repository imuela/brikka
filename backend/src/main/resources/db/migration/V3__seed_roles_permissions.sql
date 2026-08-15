-- Seed roles and the full atomic permission catalog from
-- 14_DEFINITIVE_PERMISSION_CATALOG.md. role_permissions grants are
-- intentionally NOT seeded here: the catalog defines permissions as atomic
-- capabilities but does not specify which permissions each role receives.
-- Inventing that mapping would be inventing a business rule. This is an
-- open decision to resolve before RBAC becomes functional in Sprint 2.

INSERT INTO roles (id, code, name) VALUES
    (gen_random_uuid(), 'SUPERADMIN', 'Superadministrador de plataforma'),
    (gen_random_uuid(), 'MANAGER', 'Administrador de empresa'),
    (gen_random_uuid(), 'BROKER', 'Broker'),
    (gen_random_uuid(), 'CLIENT', 'Cliente (Portal Cliente)');

INSERT INTO permissions (id, code, name) VALUES
    -- 3. Plataforma
    (gen_random_uuid(), 'COMPANY_CREATE', 'Crear empresa'),
    (gen_random_uuid(), 'COMPANY_READ', 'Consultar empresa'),
    (gen_random_uuid(), 'COMPANY_UPDATE', 'Actualizar empresa'),
    (gen_random_uuid(), 'COMPANY_SUSPEND', 'Suspender empresa'),
    (gen_random_uuid(), 'COMPANY_DELETE', 'Eliminar empresa'),
    -- 3B. Planes y suscripciones (ADR-PLATFORM-001)
    (gen_random_uuid(), 'PLAN_READ', 'Consultar planes'),
    (gen_random_uuid(), 'PLAN_MANAGE', 'Gestionar planes'),
    (gen_random_uuid(), 'SUBSCRIPTION_READ', 'Consultar suscripción de empresa'),
    (gen_random_uuid(), 'SUBSCRIPTION_MANAGE', 'Gestionar suscripción de empresa'),
    -- 4. Usuarios
    (gen_random_uuid(), 'USER_CREATE', 'Crear usuario'),
    (gen_random_uuid(), 'USER_READ', 'Consultar usuario'),
    (gen_random_uuid(), 'USER_UPDATE', 'Actualizar usuario'),
    (gen_random_uuid(), 'USER_DISABLE', 'Desactivar usuario'),
    (gen_random_uuid(), 'USER_ASSIGN_ROLE', 'Asignar rol a usuario'),
    -- 5. Clientes
    (gen_random_uuid(), 'CLIENT_CREATE', 'Crear cliente'),
    (gen_random_uuid(), 'CLIENT_READ', 'Consultar cliente'),
    (gen_random_uuid(), 'CLIENT_UPDATE', 'Actualizar cliente'),
    (gen_random_uuid(), 'CLIENT_EXPORT', 'Exportar datos de cliente'),
    (gen_random_uuid(), 'CLIENT_DELETE', 'Eliminar cliente'),
    -- 6. Operaciones
    (gen_random_uuid(), 'CASE_CREATE', 'Crear operación'),
    (gen_random_uuid(), 'CASE_READ', 'Consultar operación'),
    (gen_random_uuid(), 'CASE_UPDATE', 'Actualizar operación'),
    (gen_random_uuid(), 'CASE_ASSIGN', 'Asignar operación'),
    (gen_random_uuid(), 'CASE_CHANGE_STATUS', 'Cambiar estado de operación'),
    (gen_random_uuid(), 'CASE_CANCEL', 'Cancelar operación'),
    (gen_random_uuid(), 'CASE_REOPEN', 'Reabrir operación'),
    (gen_random_uuid(), 'CASE_EXPORT', 'Exportar operación'),
    -- 7. Inmuebles
    (gen_random_uuid(), 'PROPERTY_CREATE', 'Crear inmueble'),
    (gen_random_uuid(), 'PROPERTY_READ', 'Consultar inmueble'),
    (gen_random_uuid(), 'PROPERTY_UPDATE', 'Actualizar inmueble'),
    (gen_random_uuid(), 'PROPERTY_DELETE', 'Eliminar inmueble'),
    -- 8. Documentos
    (gen_random_uuid(), 'DOCUMENT_READ', 'Consultar documento'),
    (gen_random_uuid(), 'DOCUMENT_CREATE', 'Crear documento'),
    (gen_random_uuid(), 'DOCUMENT_REQUEST', 'Solicitar documento'),
    (gen_random_uuid(), 'DOCUMENT_UPLOAD', 'Subir documento'),
    (gen_random_uuid(), 'DOCUMENT_DOWNLOAD', 'Descargar documento'),
    (gen_random_uuid(), 'DOCUMENT_REVIEW', 'Revisar documento'),
    (gen_random_uuid(), 'DOCUMENT_APPROVE', 'Aprobar documento'),
    (gen_random_uuid(), 'DOCUMENT_REJECT', 'Rechazar documento'),
    (gen_random_uuid(), 'DOCUMENT_DELETE', 'Eliminar documento'),
    (gen_random_uuid(), 'DOCUMENT_PUBLISH', 'Publicar documento en Portal Cliente'),
    (gen_random_uuid(), 'DOCUMENT_UNPUBLISH', 'Despublicar documento del Portal Cliente'),
    (gen_random_uuid(), 'DOCUMENT_REQUIREMENT_READ', 'Consultar catálogo de requisitos documentales'),
    (gen_random_uuid(), 'DOCUMENT_REQUIREMENT_MANAGE', 'Gestionar catálogo de requisitos documentales'),
    -- 9. Financiación
    (gen_random_uuid(), 'SIMULATION_CREATE', 'Crear simulación'),
    (gen_random_uuid(), 'SIMULATION_READ', 'Consultar simulación'),
    (gen_random_uuid(), 'SIMULATION_UPDATE', 'Actualizar simulación'),
    (gen_random_uuid(), 'FINANCING_REQUEST_CREATE', 'Crear solicitud de financiación'),
    (gen_random_uuid(), 'FINANCING_REQUEST_READ', 'Consultar solicitud de financiación'),
    (gen_random_uuid(), 'FINANCING_REQUEST_UPDATE', 'Actualizar solicitud de financiación'),
    (gen_random_uuid(), 'FINANCING_FINALIZE', 'Formalizar financiación final'),
    -- 10. Bancos
    (gen_random_uuid(), 'BANK_READ', 'Consultar banco'),
    (gen_random_uuid(), 'BANK_CREATE', 'Crear banco'),
    (gen_random_uuid(), 'BANK_UPDATE', 'Actualizar banco'),
    (gen_random_uuid(), 'BANK_CRITERIA_READ', 'Consultar criterios bancarios'),
    (gen_random_uuid(), 'BANK_CRITERIA_MANAGE', 'Gestionar criterios bancarios'),
    (gen_random_uuid(), 'BANK_REQUEST_CREATE', 'Crear solicitud bancaria'),
    (gen_random_uuid(), 'BANK_REQUEST_READ', 'Consultar solicitud bancaria'),
    (gen_random_uuid(), 'BANK_RESPONSE_REGISTER', 'Registrar respuesta bancaria'),
    (gen_random_uuid(), 'BANK_OFFER_CREATE', 'Crear oferta bancaria'),
    (gen_random_uuid(), 'BANK_OFFER_READ', 'Consultar oferta bancaria'),
    (gen_random_uuid(), 'BANK_OFFER_SELECT', 'Seleccionar oferta bancaria'),
    -- 10B. Contactos bancarios
    (gen_random_uuid(), 'BANK_CONTACT_CREATE', 'Crear contacto bancario'),
    (gen_random_uuid(), 'BANK_CONTACT_READ', 'Consultar contacto bancario'),
    (gen_random_uuid(), 'BANK_CONTACT_UPDATE', 'Actualizar contacto bancario'),
    (gen_random_uuid(), 'BANK_CONTACT_DELETE', 'Eliminar contacto bancario'),
    -- 11. Tareas
    (gen_random_uuid(), 'TASK_CREATE', 'Crear tarea'),
    (gen_random_uuid(), 'TASK_READ', 'Consultar tarea'),
    (gen_random_uuid(), 'TASK_UPDATE', 'Actualizar tarea'),
    (gen_random_uuid(), 'TASK_ASSIGN', 'Asignar tarea'),
    (gen_random_uuid(), 'TASK_COMPLETE', 'Completar tarea'),
    (gen_random_uuid(), 'TASK_DELETE', 'Eliminar tarea'),
    -- 12. Comunicación
    (gen_random_uuid(), 'CONVERSATION_CREATE', 'Crear conversación'),
    (gen_random_uuid(), 'CONVERSATION_READ', 'Consultar conversación'),
    (gen_random_uuid(), 'CONVERSATION_PARTICIPANT_MANAGE', 'Gestionar participantes de conversación'),
    (gen_random_uuid(), 'MESSAGE_SEND', 'Enviar mensaje'),
    (gen_random_uuid(), 'MESSAGE_READ', 'Consultar mensaje'),
    (gen_random_uuid(), 'MESSAGE_ATTACHMENT_UPLOAD', 'Subir adjunto de mensaje'),
    (gen_random_uuid(), 'MESSAGE_ATTACHMENT_DOWNLOAD', 'Descargar adjunto de mensaje'),
    -- 13. Notificaciones
    (gen_random_uuid(), 'NOTIFICATION_READ', 'Consultar notificación'),
    (gen_random_uuid(), 'NOTIFICATION_MANAGE', 'Gestionar notificaciones'),
    -- 13B. Actividad (ADR-AUDIT-001)
    (gen_random_uuid(), 'ACTIVITY_READ', 'Consultar timeline de actividad funcional'),
    -- 14. Scoring
    (gen_random_uuid(), 'SCORING_RUN', 'Ejecutar scoring'),
    (gen_random_uuid(), 'SCORING_READ', 'Consultar resultado de scoring'),
    (gen_random_uuid(), 'SCORING_RULESET_READ', 'Consultar reglas de scoring'),
    (gen_random_uuid(), 'SCORING_RULESET_MANAGE', 'Gestionar reglas de scoring'),
    -- 15. Auditoría
    (gen_random_uuid(), 'AUDIT_READ', 'Consultar auditoría'),
    (gen_random_uuid(), 'AUDIT_EXPORT', 'Exportar auditoría'),
    -- 16. IA
    (gen_random_uuid(), 'AI_USE', 'Usar funciones de IA'),
    (gen_random_uuid(), 'AI_DOCUMENT_ANALYZE', 'Analizar documento con IA'),
    (gen_random_uuid(), 'AI_SUMMARIZE', 'Resumir con IA'),
    (gen_random_uuid(), 'AI_DRAFT_MESSAGE', 'Generar borrador de mensaje con IA'),
    (gen_random_uuid(), 'AI_MANAGE_CONFIGURATION', 'Gestionar configuración de IA'),
    (gen_random_uuid(), 'AI_READ_USAGE', 'Consultar consumo de IA'),
    -- 17. Reporting
    (gen_random_uuid(), 'REPORT_READ', 'Consultar informe'),
    (gen_random_uuid(), 'REPORT_EXPORT', 'Exportar informe'),
    -- 18. Integraciones
    (gen_random_uuid(), 'INTEGRATION_READ', 'Consultar integración'),
    (gen_random_uuid(), 'INTEGRATION_MANAGE', 'Gestionar integración'),
    (gen_random_uuid(), 'INTEGRATION_EXECUTE', 'Ejecutar integración'),
    -- 19. Portal Cliente
    (gen_random_uuid(), 'PORTAL_DASHBOARD_READ', 'Consultar dashboard del Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_CASE_READ', 'Consultar operación publicada en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_DOCUMENT_READ', 'Consultar documento en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_DOCUMENT_UPLOAD', 'Subir documento en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_DOCUMENT_REQUEST_RESPOND', 'Responder solicitud documental en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_MESSAGE_READ', 'Consultar mensaje en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_MESSAGE_SEND', 'Enviar mensaje en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_MESSAGE_ATTACHMENT_UPLOAD', 'Subir adjunto de mensaje en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_NOTIFICATION_READ', 'Consultar notificación en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_PROFILE_READ', 'Consultar perfil en el Portal Cliente'),
    (gen_random_uuid(), 'PORTAL_PROFILE_UPDATE', 'Actualizar perfil en el Portal Cliente');
