package com.brika.platform.identity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RolePermissionRepository {

  private final JdbcTemplate jdbcTemplate;

  public RolePermissionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Set<String> permissionCodesForRole(UUID roleId) {
    return new HashSet<>(
        jdbcTemplate.queryForList(
            "SELECT p.code FROM role_permissions rp JOIN permissions p ON p.id ="
                + " rp.permission_id WHERE rp.role_id = ?",
            String.class,
            roleId));
  }

  public int count() {
    Integer count =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class);
    return count == null ? 0 : count;
  }
}
