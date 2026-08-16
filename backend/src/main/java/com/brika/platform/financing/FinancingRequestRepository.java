package com.brika.platform.financing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FinancingRequestRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, status, requested_amount, term_months, created_at,"
          + " updated_at FROM financing_requests";

  private static final RowMapper<FinancingRequest> ROW_MAPPER =
      (rs, rowNum) ->
          new FinancingRequest(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              rs.getString("status"),
              rs.getBigDecimal("requested_amount"),
              rs.getInt("term_months"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public FinancingRequestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID companyId, UUID caseId, BigDecimal requestedAmount, int termMonths) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO financing_requests (company_id, case_id, status, requested_amount,"
            + " term_months) VALUES (?, ?, 'PENDING', ?, ?) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        requestedAmount,
        termMonths);
  }

  public Optional<FinancingRequest> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<FinancingRequest> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY created_at DESC", ROW_MAPPER, caseId);
  }

  public void update(UUID id, String status, BigDecimal requestedAmount, int termMonths) {
    jdbcTemplate.update(
        "UPDATE financing_requests SET status = ?, requested_amount = ?, term_months = ?,"
            + " updated_at = now() WHERE id = ?",
        status,
        requestedAmount,
        termMonths,
        id);
  }
}
