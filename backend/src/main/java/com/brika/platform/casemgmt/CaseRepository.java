package com.brika.platform.casemgmt;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CaseRepository {

  private static final String SELECT =
      "SELECT id, company_id, reference, status, operation_type, requested_amount, description,"
          + " created_by, created_at, cancelled_at FROM cases";

  private static final RowMapper<Case> ROW_MAPPER =
      (rs, rowNum) ->
          new Case(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              rs.getString("reference"),
              CaseStatus.valueOf(rs.getString("status")),
              rs.getString("operation_type"),
              rs.getBigDecimal("requested_amount"),
              rs.getString("description"),
              (UUID) rs.getObject("created_by"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("cancelled_at") == null
                  ? null
                  : rs.getTimestamp("cancelled_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public CaseRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * reference is always server-generated (never accepted from a client — no doc specifies
   * caller-supplied references).
   */
  public UUID insert(
      UUID companyId,
      String reference,
      String operationType,
      java.math.BigDecimal requestedAmount,
      String description,
      UUID createdBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO cases (company_id, reference, status, operation_type, requested_amount,"
            + " description, created_by) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        reference,
        CaseStatus.PRESTUDY.name(),
        operationType,
        requestedAmount,
        description,
        createdBy);
  }

  public Optional<Case> findById(UUID id) {
    List<Case> cases = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return cases.stream().findFirst();
  }

  public List<Case> findAllByCompanyId(UUID companyId) {
    return jdbcTemplate.query(
        SELECT + " WHERE company_id = ? ORDER BY created_at DESC", ROW_MAPPER, companyId);
  }

  /** Sprint 27 (ADR-RBAC-002): GLOBAL read for SUPERADMIN across all companies. */
  public List<Case> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY created_at DESC", ROW_MAPPER);
  }

  public List<Case> findAllAssignedToUser(UUID companyId, UUID userId) {
    return jdbcTemplate.query(
        SELECT
            + " WHERE company_id = ? AND id IN (SELECT case_id FROM case_assignments WHERE"
            + " user_id = ? AND active = true) ORDER BY created_at DESC",
        ROW_MAPPER,
        companyId,
        userId);
  }

  /** Portal Cliente (Sprint 7): cases where the client is a case_clients participant. */
  public List<Case> findAllByClientId(UUID companyId, UUID clientId) {
    return jdbcTemplate.query(
        SELECT
            + " WHERE company_id = ? AND id IN (SELECT case_id FROM case_clients WHERE client_id"
            + " = ?) ORDER BY created_at DESC",
        ROW_MAPPER,
        companyId,
        clientId);
  }

  public void updateOperationType(UUID id, String operationType) {
    jdbcTemplate.update(
        "UPDATE cases SET operation_type = ?, updated_at = now() WHERE id = ?", operationType, id);
  }

  /** Sprint 27, Bloque 4: PATCH updates the operation's editable details (type, amount, notes). */
  public void updateDetails(
      UUID id, String operationType, java.math.BigDecimal requestedAmount, String description) {
    jdbcTemplate.update(
        "UPDATE cases SET operation_type = ?, requested_amount = ?, description = ?, updated_at"
            + " = now() WHERE id = ?",
        operationType,
        requestedAmount,
        description,
        id);
  }

  /** cancelledAt is set on transition to CANCELLED, and cleared (null) when reopening from it. */
  public void updateStatus(UUID id, CaseStatus newStatus, Instant cancelledAt) {
    jdbcTemplate.update(
        "UPDATE cases SET status = ?, cancelled_at = ?, updated_at = now() WHERE id = ?",
        newStatus.name(),
        cancelledAt == null ? null : Timestamp.from(cancelledAt),
        id);
  }
}
