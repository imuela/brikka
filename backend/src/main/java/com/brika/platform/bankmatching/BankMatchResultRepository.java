package com.brika.platform.bankmatching;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BankMatchResultRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, bank_id, bank_criteria_version_id, global_result,"
          + " input_snapshot, evaluated_by, evaluated_at, created_at FROM bank_match_results";

  private static final RowMapper<BankMatchResult> ROW_MAPPER =
      (rs, rowNum) ->
          new BankMatchResult(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("bank_id"),
              (UUID) rs.getObject("bank_criteria_version_id"),
              rs.getString("global_result"),
              rs.getString("input_snapshot"),
              (UUID) rs.getObject("evaluated_by"),
              rs.getTimestamp("evaluated_at").toInstant(),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public BankMatchResultRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID caseId,
      UUID bankId,
      UUID bankCriteriaVersionId,
      String globalResult,
      String inputSnapshotJson,
      UUID evaluatedBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_match_results (company_id, case_id, bank_id, bank_criteria_version_id,"
            + " global_result, input_snapshot, evaluated_by) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)"
            + " RETURNING id",
        UUID.class,
        companyId,
        caseId,
        bankId,
        bankCriteriaVersionId,
        globalResult,
        inputSnapshotJson,
        evaluatedBy);
  }

  public Optional<BankMatchResult> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<BankMatchResult> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY evaluated_at DESC", ROW_MAPPER, caseId);
  }
}
