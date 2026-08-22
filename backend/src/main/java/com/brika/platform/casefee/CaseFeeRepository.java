package com.brika.platform.casefee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CaseFeeRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, fee_type, fixed_amount, percentage, calculation_base,"
          + " calculated_amount, status, agreed_at, updated_by, created_at, updated_at"
          + " FROM case_fees";

  private static final RowMapper<CaseFee> ROW_MAPPER =
      (rs, rowNum) ->
          new CaseFee(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              rs.getString("fee_type"),
              rs.getBigDecimal("fixed_amount"),
              rs.getBigDecimal("percentage"),
              rs.getBigDecimal("calculation_base"),
              rs.getBigDecimal("calculated_amount"),
              rs.getString("status"),
              toInstant(rs.getTimestamp("agreed_at")),
              (UUID) rs.getObject("updated_by"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public CaseFeeRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<CaseFee> findByCaseId(UUID caseId) {
    return jdbcTemplate.query(SELECT + " WHERE case_id = ?", ROW_MAPPER, caseId).stream()
        .findFirst();
  }

  public UUID insert(
      UUID companyId,
      UUID caseId,
      String feeType,
      BigDecimal fixedAmount,
      BigDecimal percentage,
      BigDecimal calculationBase,
      BigDecimal calculatedAmount,
      String status,
      Instant agreedAt,
      UUID updatedBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO case_fees (company_id, case_id, fee_type, fixed_amount, percentage,"
            + " calculation_base, calculated_amount, status, agreed_at, updated_by) VALUES (?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        feeType,
        fixedAmount,
        percentage,
        calculationBase,
        calculatedAmount,
        status,
        agreedAt,
        updatedBy);
  }

  public void update(
      UUID id,
      String feeType,
      BigDecimal fixedAmount,
      BigDecimal percentage,
      BigDecimal calculationBase,
      BigDecimal calculatedAmount,
      String status,
      Instant agreedAt,
      UUID updatedBy) {
    jdbcTemplate.update(
        "UPDATE case_fees SET fee_type = ?, fixed_amount = ?, percentage = ?,"
            + " calculation_base = ?, calculated_amount = ?, status = ?, agreed_at = ?,"
            + " updated_by = ?, updated_at = now() WHERE id = ?",
        feeType,
        fixedAmount,
        percentage,
        calculationBase,
        calculatedAmount,
        status,
        agreedAt,
        updatedBy,
        id);
  }

  private static Instant toInstant(java.sql.Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
