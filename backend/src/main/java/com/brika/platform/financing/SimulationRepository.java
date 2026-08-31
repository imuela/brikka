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
          + " interest_type, base_interest_rate, final_interest_rate, euribor_rate, spread_rate,"
          + " fixed_period_months, fixed_period_rate, ico_guarantee, bonifications, metadata,"
          + " created_by, created_at FROM simulations";

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
              rs.getString("interest_type"),
              rs.getBigDecimal("base_interest_rate"),
              rs.getBigDecimal("final_interest_rate"),
              rs.getBigDecimal("euribor_rate"),
              rs.getBigDecimal("spread_rate"),
              (Integer) rs.getObject("fixed_period_months"),
              rs.getBigDecimal("fixed_period_rate"),
              rs.getBoolean("ico_guarantee"),
              rs.getString("bonifications"),
              rs.getString("metadata"),
              (UUID) rs.getObject("created_by"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public SimulationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * BRIKKA V2 I4. Persists an enriched simulation: {@code interest_rate} and {@code
   * final_interest_rate} both take the computed effective rate; {@code estimated_payment} is the
   * computed (French-amortization) payment; {@code euribor_rate} / {@code spread_rate} / {@code
   * fixed_period_*} are stored only for the types that use them (they are {@code null} in the model
   * otherwise).
   */
  public UUID insert(
      UUID companyId,
      UUID caseId,
      UUID createdBy,
      SimulationInterestModel model,
      SimulationComputation computation,
      String bonificationsJson,
      boolean icoGuarantee,
      String metadataJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO simulations (company_id, case_id, principal, interest_rate, term_months,"
            + " estimated_payment, interest_type, base_interest_rate, final_interest_rate,"
            + " euribor_rate, spread_rate, fixed_period_months, fixed_period_rate, ico_guarantee,"
            + " bonifications, metadata, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?::jsonb, ?::jsonb, ?) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        model.principal(),
        computation.finalInterestRate(),
        model.termMonths(),
        computation.estimatedPayment(),
        model.type().name(),
        computation.baseInterestRate(),
        computation.finalInterestRate(),
        model.euriborRate(),
        model.spreadRate(),
        model.fixedPeriodMonths(),
        model.fixedPeriodRate(),
        icoGuarantee,
        bonificationsJson,
        metadataJson,
        createdBy);
  }

  /**
   * Low-level FIXED insert kept for callers that only need a minimal flat simulation (e.g. seeding
   * a case for an unrelated feature's test). {@code base_interest_rate} and {@code
   * final_interest_rate} both take {@code interestRate}; no bonifications, no ICO.
   */
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
            + " estimated_payment, interest_type, base_interest_rate, final_interest_rate,"
            + " bonifications, metadata, created_by) VALUES (?, ?, ?, ?, ?, ?, 'FIXED', ?, ?,"
            + " '[]'::jsonb, ?::jsonb, ?) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        principal,
        interestRate,
        termMonths,
        estimatedPayment,
        interestRate,
        interestRate,
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
