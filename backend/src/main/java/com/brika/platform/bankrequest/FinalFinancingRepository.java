package com.brika.platform.bankrequest;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Decision D3 (Option 2): selecting an offer never creates a second row for a case that already has
 * one — case_id is UNIQUE at the database level. The controller looks up by caseId first and either
 * inserts or calls updateBankOffer. status has no documented catalog; defaulted once to 'ACTIVE' at
 * creation and left unchanged on re-selection (only bank_offer_id/finalized_at move).
 */
@Repository
public class FinalFinancingRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, bank_offer_id, status, finalized_at, created_at,"
          + " updated_at FROM final_financing";

  private static final RowMapper<FinalFinancing> ROW_MAPPER =
      (rs, rowNum) ->
          new FinalFinancing(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("bank_offer_id"),
              rs.getString("status"),
              rs.getTimestamp("finalized_at") == null
                  ? null
                  : rs.getTimestamp("finalized_at").toInstant(),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public FinalFinancingRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID companyId, UUID caseId, UUID bankOfferId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO final_financing (company_id, case_id, bank_offer_id, status, finalized_at)"
            + " VALUES (?, ?, ?, 'ACTIVE', now()) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        bankOfferId);
  }

  public void updateBankOffer(UUID id, UUID bankOfferId) {
    jdbcTemplate.update(
        "UPDATE final_financing SET bank_offer_id = ?, finalized_at = now(), updated_at = now()"
            + " WHERE id = ?",
        bankOfferId,
        id);
  }

  public Optional<FinalFinancing> findByCaseId(UUID caseId) {
    return jdbcTemplate.query(SELECT + " WHERE case_id = ?", ROW_MAPPER, caseId).stream()
        .findFirst();
  }

  public Optional<FinalFinancing> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }
}
