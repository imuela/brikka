-- Sprint 10 D10-1: grants the 4 AI "use" permissions (already cataloged in V3 since before Sprint 6,
-- previously PENDING per ADR-RBAC-001) to SUPERADMIN, MANAGER and BROKER. No new permission codes
-- are created here — only role_permissions grants for codes that already exist.
-- AI_MANAGE_CONFIGURATION and AI_READ_USAGE remain SUPERADMIN-only, unchanged from V9. CLIENT
-- receives none of the six AI_* permissions.

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
  VALUES
    ('SUPERADMIN', 'AI_USE'),
    ('MANAGER', 'AI_USE'),
    ('BROKER', 'AI_USE'),
    ('SUPERADMIN', 'AI_DOCUMENT_ANALYZE'),
    ('MANAGER', 'AI_DOCUMENT_ANALYZE'),
    ('BROKER', 'AI_DOCUMENT_ANALYZE'),
    ('SUPERADMIN', 'AI_SUMMARIZE'),
    ('MANAGER', 'AI_SUMMARIZE'),
    ('BROKER', 'AI_SUMMARIZE'),
    ('SUPERADMIN', 'AI_DRAFT_MESSAGE'),
    ('MANAGER', 'AI_DRAFT_MESSAGE'),
    ('BROKER', 'AI_DRAFT_MESSAGE')
) AS approved (role_code, permission_code)
JOIN roles r ON r.code = approved.role_code
JOIN permissions p ON p.code = approved.permission_code;
