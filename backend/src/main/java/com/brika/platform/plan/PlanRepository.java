package com.brika.platform.plan;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlanRepository {

  private final JdbcTemplate jdbcTemplate;

  public PlanRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(String code, String name, String status) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO plans (code, name, status) VALUES (?, ?, ?) RETURNING id",
        UUID.class,
        code,
        name,
        status);
  }

  public Plan findByCode(String code) {
    return jdbcTemplate.queryForObject(
        "SELECT id, code, name, status FROM plans WHERE code = ?",
        (rs, rowNum) ->
            new Plan(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("status")),
        code);
  }
}
