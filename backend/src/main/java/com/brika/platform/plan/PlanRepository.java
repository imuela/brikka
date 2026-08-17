package com.brika.platform.plan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PlanRepository {

  private static final String SELECT = "SELECT id, code, name, status FROM plans";

  private static final RowMapper<Plan> PLAN_ROW_MAPPER = PlanRepository::mapPlan;

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
    return jdbcTemplate.queryForObject(SELECT + " WHERE code = ?", PLAN_ROW_MAPPER, code);
  }

  public Optional<Plan> findById(UUID id) {
    List<Plan> plans = jdbcTemplate.query(SELECT + " WHERE id = ?", PLAN_ROW_MAPPER, id);
    return plans.stream().findFirst();
  }

  public List<Plan> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY name", PLAN_ROW_MAPPER);
  }

  public void update(UUID id, String name, String status) {
    jdbcTemplate.update(
        "UPDATE plans SET name = ?, status = ?, updated_at = now() WHERE id = ?", name, status, id);
  }

  private static Plan mapPlan(ResultSet rs, int rowNum) throws SQLException {
    return new Plan(
        (UUID) rs.getObject("id"),
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("status"));
  }
}
