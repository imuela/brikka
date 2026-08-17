package com.brika.platform.bankmatching;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BankMatchRuleOverrideRepository {

  private static final String SELECT =
      "SELECT id, company_id, bank_match_rule_result_id, previous_result, new_result, reason,"
          + " overridden_by, overridden_at, created_at FROM bank_match_rule_overrides";

  private static final RowMapper<BankMatchRuleOverride> ROW_MAPPER =
      (rs, rowNum) ->
          new BankMatchRuleOverride(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("bank_match_rule_result_id"),
              rs.getString("previous_result"),
              rs.getString("new_result"),
              rs.getString("reason"),
              (UUID) rs.getObject("overridden_by"),
              rs.getTimestamp("overridden_at").toInstant(),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public BankMatchRuleOverrideRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID bankMatchRuleResultId,
      String previousResult,
      String newResult,
      String reason,
      UUID overriddenBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_match_rule_overrides (company_id, bank_match_rule_result_id,"
            + " previous_result, new_result, reason, overridden_by) VALUES (?, ?, ?, ?, ?, ?)"
            + " RETURNING id",
        UUID.class,
        companyId,
        bankMatchRuleResultId,
        previousResult,
        newResult,
        reason,
        overriddenBy);
  }

  public Optional<BankMatchRuleOverride> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  /**
   * Full history in chronological order (oldest first) — the most recent entry is the effective
   * one.
   */
  public List<BankMatchRuleOverride> findAllByRuleResultId(UUID ruleResultId) {
    return jdbcTemplate.query(
        SELECT + " WHERE bank_match_rule_result_id = ? ORDER BY overridden_at ASC",
        ROW_MAPPER,
        ruleResultId);
  }
}
