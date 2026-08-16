package com.brika.platform.identity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Pure RBAC query (user_roles -> role_permissions -> permissions), per ADR-RBAC-001. Not wired to
 * any authenticated principal, filter, or endpoint yet — that requires OAuth2/JWT (out of scope for
 * this block) and TenantContext resolved from the real token.
 */
@Service
public class PermissionResolutionService {

  private final JdbcTemplate jdbcTemplate;

  public PermissionResolutionService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Set<String> permissionCodesForUser(UUID userId) {
    return new HashSet<>(
        jdbcTemplate.queryForList(
            "SELECT DISTINCT p.code FROM user_roles ur"
                + " JOIN role_permissions rp ON rp.role_id = ur.role_id"
                + " JOIN permissions p ON p.id = rp.permission_id"
                + " WHERE ur.user_id = ?",
            String.class,
            userId));
  }
}
