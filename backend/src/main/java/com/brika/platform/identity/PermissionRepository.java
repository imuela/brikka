package com.brika.platform.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PermissionRepository {

  private final JdbcTemplate jdbcTemplate;

  public PermissionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Permission> findAll() {
    return jdbcTemplate.query(
        "SELECT id, code, name FROM permissions ORDER BY code",
        (rs, rowNum) ->
            new Permission((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name")));
  }
}
