-- BRIKKA V2 · Bloque I3 (precondiciones de transición de Case).
--
-- Fuente funcional: BRIKKA_V2_MIGRATION_SCOPE.md §1 I3 + 13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §5
-- ("Debe existir la documentación mínima ... o una excepción autorizada"). Los tres gates
-- (DOCUMENTATION→ANALYSIS, BANK_SEARCH→BANK_SUBMISSION, OFFER→FORMALIZATION) se validan en el
-- backend; una transición bloqueada puede forzarse SÓLO con este permiso + motivo obligatorio, y
-- queda registrada en case_status_history.reason con el marcador "[PRECONDITION_OVERRIDE] " y en el
-- evento de auditoría CASE_STATUS_CHANGED (mecanismos ya existentes — no se crea auditoría paralela).
--
-- Roles: MANAGER y SUPERADMIN, exactamente el mismo criterio que BANK_MATCHING_OVERRIDE (V14) —
-- una excepción de nivel de gestión, nunca BROKER ni CLIENT.

INSERT INTO permissions (id, code, name) VALUES
    (gen_random_uuid(), 'CASE_TRANSITION_OVERRIDE',
     'Forzar una transición de estado de operación saltándose una precondición de negocio');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
  VALUES
    ('MANAGER', 'CASE_TRANSITION_OVERRIDE'),
    ('SUPERADMIN', 'CASE_TRANSITION_OVERRIDE')
) AS approved (role_code, permission_code)
JOIN roles r ON r.code = approved.role_code
JOIN permissions p ON p.code = approved.permission_code;
