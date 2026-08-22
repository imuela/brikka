package com.brika.platform.casefee;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CaseFeeHistoryRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, fee_id, fee_type, fixed_amount, percentage,"
          + " calculation_base, calculated_amount, status, agreed_at, changed_by, changed_at"
          + " FROM case_fee_history";

  private static final RowMapper<CaseFeeHistoryEntry> ROW_MAPPER =
      (rs, rowNum) ->
          new CaseFeeHistoryEntry(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("fee_id"),
              rs.getString("fee_type"),
              rs.getBigDecimal("fixed_amount"),
              rs.getBigDecimal("percentage"),
              rs.getBigDecimal("calculation_base"),
              rs.getBigDecimal("calculated_amount"),
              rs.getString("status"),
              toInstant(rs.getTimestamp("agreed_at")),
              (UUID) rs.getObject("changed_by"),
              rs.getTimestamp("changed_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public CaseFeeHistoryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insert(CaseFee snapshot, UUID changedBy) {
    jdbcTemplate.update(
        "INSERT INTO case_fee_history (company_id, case_id, fee_id, fee_type, fixed_amount,"
            + " percentage, calculation_base, calculated_amount, status, agreed_at, changed_by)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        snapshot.companyId(),
        snapshot.caseId(),
        snapshot.id(),
        snapshot.feeType(),
        snapshot.fixedAmount(),
        snapshot.percentage(),
        snapshot.calculationBase(),
        snapshot.calculatedAmount(),
        snapshot.status(),
        snapshot.agreedAt() == null ? null : Timestamp.from(snapshot.agreedAt()),
        changedBy);
  }

  public List<CaseFeeHistoryEntry> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY changed_at DESC", ROW_MAPPER, caseId);
  }

  private static java.time.Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
