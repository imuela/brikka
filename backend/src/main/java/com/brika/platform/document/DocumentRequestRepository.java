package com.brika.platform.document;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRequestRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, document_type_id, requested_from_client_id, status,"
          + " due_at, requested_by, requirement_id FROM document_requests";

  private static final RowMapper<DocumentRequest> ROW_MAPPER =
      (rs, rowNum) ->
          new DocumentRequest(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("document_type_id"),
              (UUID) rs.getObject("requested_from_client_id"),
              DocumentRequestStatus.valueOf(rs.getString("status")),
              rs.getTimestamp("due_at") == null ? null : rs.getTimestamp("due_at").toInstant(),
              (UUID) rs.getObject("requested_by"),
              (UUID) rs.getObject("requirement_id"));

  private final JdbcTemplate jdbcTemplate;

  public DocumentRequestRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID caseId,
      UUID documentTypeId,
      UUID requestedFromClientId,
      Instant dueAt,
      UUID requestedBy,
      UUID requirementId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO document_requests (company_id, case_id, document_type_id,"
            + " requested_from_client_id, status, due_at, requested_by, requirement_id) VALUES"
            + " (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        documentTypeId,
        requestedFromClientId,
        DocumentRequestStatus.PENDING.name(),
        dueAt == null ? null : Timestamp.from(dueAt),
        requestedBy,
        requirementId);
  }

  public Optional<DocumentRequest> findById(UUID id) {
    List<DocumentRequest> requests = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return requests.stream().findFirst();
  }

  public List<DocumentRequest> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(SELECT + " WHERE case_id = ? ORDER BY due_at", ROW_MAPPER, caseId);
  }

  /**
   * Sprint 19 (ADR-PROCESS-007): Portal Cliente "Solicitudes de documentación" — a case can have
   * several clients (holder/co-holder), each only ever sees requests addressed to them.
   */
  public List<DocumentRequest> findAllByCaseIdAndClientId(UUID caseId, UUID clientId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? AND requested_from_client_id = ? ORDER BY due_at",
        ROW_MAPPER,
        caseId,
        clientId);
  }

  /**
   * Portal Cliente upload (Sprint 7): opportunistic match to auto-fulfill a pending request when
   * the client uploads a document of the requested type — heuristic on (case, type, client) since
   * document_requests has no FK back to the document that satisfies it.
   */
  public Optional<DocumentRequest> findPendingByCaseAndTypeAndClient(
      UUID caseId, UUID documentTypeId, UUID clientId) {
    List<DocumentRequest> requests =
        jdbcTemplate.query(
            SELECT
                + " WHERE case_id = ? AND document_type_id = ? AND requested_from_client_id = ?"
                + " AND status = 'PENDING' ORDER BY due_at",
            ROW_MAPPER,
            caseId,
            documentTypeId,
            clientId);
    return requests.stream().findFirst();
  }

  public void updateStatus(UUID id, DocumentRequestStatus status) {
    jdbcTemplate.update(
        "UPDATE document_requests SET status = ?, updated_at = now() WHERE id = ?",
        status.name(),
        id);
  }
}
