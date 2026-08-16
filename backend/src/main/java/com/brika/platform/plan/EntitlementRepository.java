package com.brika.platform.plan;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EntitlementRepository {

  private final JdbcTemplate jdbcTemplate;

  public EntitlementRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(String code, String name, String description, EntitlementValueType valueType) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO entitlements (code, name, description, value_type) VALUES (?, ?, ?, ?)"
            + " RETURNING id",
        UUID.class,
        code,
        name,
        description,
        valueType.name());
  }

  public List<Entitlement> findAll() {
    return jdbcTemplate.query(
        "SELECT id, code, name, description, value_type FROM entitlements ORDER BY code",
        (rs, rowNum) ->
            new Entitlement(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                EntitlementValueType.valueOf(rs.getString("value_type"))));
  }
}
