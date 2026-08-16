-- ADR-PORTAL-AUTH-001: CLIENT_PORTAL_ACCOUNT_CREATE, approved explicitly for
-- Sprint 7 (Portal Cliente provisioning) — not part of the original
-- ADR-RBAC-001 matrix (221 combinations). Scope TENANT, granted only to
-- MANAGER and BROKER per explicit approval; SUPERADMIN is intentionally not
-- granted this permission.

INSERT INTO permissions (id, code, name) VALUES
    (gen_random_uuid(), 'CLIENT_PORTAL_ACCOUNT_CREATE', 'Crear cuenta de Portal Cliente para un cliente');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
  VALUES
    ('MANAGER', 'CLIENT_PORTAL_ACCOUNT_CREATE'),
    ('BROKER', 'CLIENT_PORTAL_ACCOUNT_CREATE')
) AS approved (role_code, permission_code)
JOIN roles r ON r.code = approved.role_code
JOIN permissions p ON p.code = approved.permission_code;
