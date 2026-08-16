package com.brika.platform.portal;

import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * A Portal principal always has exactly the CLIENT role's permission set (ADR-PORTAL-AUTH-001) —
 * there is no per-account variation, so this is a fixed query with no parameter, deliberately kept
 * separate from PermissionResolutionService (which resolves per-user via user_roles and has no
 * meaning for a client_portal_accounts principal).
 */
@Service
public class PortalPermissionResolutionService {

  private final JdbcTemplate jdbcTemplate;

  public PortalPermissionResolutionService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Set<String> portalPermissionCodes() {
    return new HashSet<>(
        jdbcTemplate.queryForList(
            "SELECT DISTINCT p.code FROM role_permissions rp"
                + " JOIN roles r ON r.id = rp.role_id"
                + " JOIN permissions p ON p.id = rp.permission_id"
                + " WHERE r.code = 'CLIENT'",
            String.class));
  }
}
