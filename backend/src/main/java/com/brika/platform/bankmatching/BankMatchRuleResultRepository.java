package com.brika.platform.bankmatching;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BankMatchRuleResultRepository {

  private static final String SELECT =
      "SELECT id, match_result_id, rule_id, field, operator, expected_value, evaluated_value,"
          + " result, reason, created_at FROM bank_match_rule_results";

  private static final RowMapper<BankMatchRuleResult> ROW_MAPPER =
      (rs, rowNum) ->
          new BankMatchRuleResult(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("match_result_id"),
              rs.getString("rule_id"),
              rs.getString("field"),
              rs.getString("operator"),
              rs.getString("expected_value"),
              rs.getString("evaluated_value"),
              rs.getString("result"),
              rs.getString("reason"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public BankMatchRuleResultRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID matchResultId,
      String ruleId,
      String field,
      String operator,
      String expectedValueJson,
      String evaluatedValueJson,
      String result,
      String reason) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_match_rule_results (match_result_id, rule_id, field, operator,"
            + " expected_value, evaluated_value, result, reason) VALUES (?, ?, ?, ?, ?::jsonb,"
            + " ?::jsonb, ?, ?) RETURNING id",
        UUID.class,
        matchResultId,
        ruleId,
        field,
        operator,
        expectedValueJson,
        evaluatedValueJson,
        result,
        reason);
  }

  public List<BankMatchRuleResult> findAllByMatchResultId(UUID matchResultId) {
    return jdbcTemplate.query(
        SELECT + " WHERE match_result_id = ? ORDER BY created_at", ROW_MAPPER, matchResultId);
  }
}
