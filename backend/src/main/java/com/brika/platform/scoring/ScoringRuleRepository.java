package com.brika.platform.scoring;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ScoringRuleRepository {

  private static final String SELECT =
      "SELECT id, ruleset_id, code, weight, configuration FROM scoring_rules";

  private static final RowMapper<ScoringRule> ROW_MAPPER =
      (rs, rowNum) ->
          new ScoringRule(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("ruleset_id"),
              rs.getString("code"),
              rs.getBigDecimal("weight"),
              rs.getString("configuration"));

  private final JdbcTemplate jdbcTemplate;

  public ScoringRuleRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID rulesetId, String code, java.math.BigDecimal weight, String configurationJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO scoring_rules (ruleset_id, code, weight, configuration) VALUES (?, ?, ?,"
            + " ?::jsonb) RETURNING id",
        UUID.class,
        rulesetId,
        code,
        weight,
        configurationJson);
  }

  public List<ScoringRule> findAllByRulesetId(UUID rulesetId) {
    return jdbcTemplate.query(SELECT + " WHERE ruleset_id = ?", ROW_MAPPER, rulesetId);
  }
}
