package com.brika.platform.scoring;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ScoringResultRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, ruleset_id, total_score, category, explanation,"
          + " calculated_at FROM scoring_results";

  private static final RowMapper<ScoringResult> ROW_MAPPER =
      (rs, rowNum) ->
          new ScoringResult(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("ruleset_id"),
              rs.getBigDecimal("total_score"),
              rs.getString("category"),
              rs.getString("explanation"),
              rs.getTimestamp("calculated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public ScoringResultRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID caseId,
      UUID rulesetId,
      BigDecimal totalScore,
      String category,
      String explanationJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO scoring_results (company_id, case_id, ruleset_id, total_score, category,"
            + " explanation) VALUES (?, ?, ?, ?, ?, ?::jsonb) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        rulesetId,
        totalScore,
        category,
        explanationJson);
  }

  public Optional<ScoringResult> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<ScoringResult> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY calculated_at DESC", ROW_MAPPER, caseId);
  }
}
