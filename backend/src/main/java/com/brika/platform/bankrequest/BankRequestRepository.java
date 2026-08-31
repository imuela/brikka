package com.brika.platform.bankrequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * bank_requests.status has no documented catalog anywhere (no "estado" field is listed for
 * solicitudes in FUNCTIONAL_SPECIFICATION.md §16, unlike offers in §17). Sprint 6A treats it as a
 * server-controlled lifecycle marker, defaulted once at creation to 'SENT' — no endpoint in this
 * sprint changes it (no PATCH is documented). Disclosed as technical debt, not a business catalog.
 * submitted_at is set at creation time: creating the request is the only documented action, so
 * creation is interpreted as submission.
 */
@Repository
public class BankRequestRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, bank_id, bank_contact_id, status, submitted_at,"
          + " contact_snapshot, created_at, updated_at FROM bank_requests";

  private static final RowMapper<BankRequest> ROW_MAPPER =
      (rs, rowNum) ->
          new BankRequest(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("bank_id"),
              (UUID) rs.getObject("bank_contact_id"),
              rs.getString("status"),
              rs.getTimestamp("submitted_at") == null
                  ? null
                  : rs.getTimestamp("submitted_at").toInstant(),
              rs.getString("contact_snapshot"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public BankRequestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId, UUID caseId, UUID bankId, UUID bankContactId, String contactSnapshotJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_requests (company_id, case_id, bank_id, bank_contact_id, status,"
            + " submitted_at, contact_snapshot) VALUES (?, ?, ?, ?, 'SENT', now(), ?::jsonb)"
            + " RETURNING id",
        UUID.class,
        companyId,
        caseId,
        bankId,
        bankContactId,
        contactSnapshotJson);
  }

  public Optional<BankRequest> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<BankRequest> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY created_at DESC", ROW_MAPPER, caseId);
  }

  /**
   * BRIKKA V2 I3: the BANK_SEARCH -> BANK_SUBMISSION precondition (at least one bank request for
   * the case). Tenant-aware on purpose — the gate must not be satisfied by a row belonging to
   * another company, even if that row points at this case.
   */
  public boolean existsByCaseIdAndCompanyId(UUID caseId, UUID companyId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM bank_requests WHERE case_id = ? AND company_id = ?",
            Integer.class,
            caseId,
            companyId);
    return count != null && count > 0;
  }
}
