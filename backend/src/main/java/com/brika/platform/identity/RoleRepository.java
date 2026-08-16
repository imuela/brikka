package com.brika.platform.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoleRepository {

  private final JdbcTemplate jdbcTemplate;

  public RoleRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Role findByCode(String code) {
    return jdbcTemplate.queryForObject(
        "SELECT id, code, name FROM roles WHERE code = ?",
        (rs, rowNum) ->
            new Role((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name")),
        code);
  }

  public List<Role> findAll() {
    return jdbcTemplate.query(
        "SELECT id, code, name FROM roles ORDER BY code",
        (rs, rowNum) ->
            new Role((UUID) rs.getObject("id"), rs.getString("code"), rs.getString("name")));
  }
}
