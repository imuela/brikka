-- Sprint 29 (estabilización): NOTIFICATION_READ was granted to MANAGER and BROKER in V9, but never
-- to SUPERADMIN — an oversight from before Sprint 27's GLOBAL SUPERADMIN model (ADR-RBAC-002).
-- NotificationController already scopes every query to the caller's own recipient_user_id
-- regardless of role (notifications are personal, never tenant-wide), so this grant does not
-- widen SUPERADMIN's visibility into other users' notifications — it only lets SUPERADMIN read
-- their own, exactly like every other role already can.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permissions p ON p.code = 'NOTIFICATION_READ'
WHERE r.code = 'SUPERADMIN';
