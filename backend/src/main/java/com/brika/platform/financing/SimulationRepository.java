package com.brika.platform.financing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class SimulationRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, principal, interest_rate, term_months, estimated_payment,"
          + " metadata, created_by, created_at FROM simulations";

  private static final RowMapper<Simulation> ROW_MAPPER =
      (rs, rowNum) ->
          new Simulation(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              rs.getBigDecimal("principal"),
              rs.getBigDecimal("interest_rate"),
              rs.getInt("term_months"),
              rs.getBigDecimal("estimated_payment"),
              rs.getString("metadata"),
              (UUID) rs.getObject("created_by"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public SimulationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID caseId,
      BigDecimal principal,
      BigDecimal interestRate,
      int termMonths,
      BigDecimal estimatedPayment,
      String metadataJson,
      UUID createdBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO simulations (company_id, case_id, principal, interest_rate, term_months,"
            + " estimated_payment, metadata, created_by) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?)"
            + " RETURNING id",
        UUID.class,
        companyId,
        caseId,
        principal,
        interestRate,
        termMonths,
        estimatedPayment,
        metadataJson,
        createdBy);
  }

  public Optional<Simulation> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<Simulation> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY created_at DESC", ROW_MAPPER, caseId);
  }
}
