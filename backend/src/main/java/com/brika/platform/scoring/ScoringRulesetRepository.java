package com.brika.platform.scoring;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ScoringRulesetRepository {

  private static final String SELECT =
      "SELECT id, code, version, status, rules, created_at FROM scoring_rulesets";

  private static final RowMapper<ScoringRuleset> ROW_MAPPER =
      (rs, rowNum) ->
          new ScoringRuleset(
              (UUID) rs.getObject("id"),
              rs.getString("code"),
              rs.getString("version"),
              rs.getString("status"),
              rs.getString("rules"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public ScoringRulesetRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(String code, String version, String status, String categoriesJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO scoring_rulesets (code, version, status, rules) VALUES (?, ?, ?, ?::jsonb)"
            + " RETURNING id",
        UUID.class,
        code,
        version,
        status,
        categoriesJson);
  }

  public Optional<ScoringRuleset> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<ScoringRuleset> findAllActive() {
    return jdbcTemplate.query(SELECT + " WHERE status = 'ACTIVE' ORDER BY created_at", ROW_MAPPER);
  }

  public List<ScoringRuleset> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY created_at", ROW_MAPPER);
  }
}
