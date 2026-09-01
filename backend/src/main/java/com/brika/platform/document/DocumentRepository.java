package com.brika.platform.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, document_type_id, client_id, current_version_id, status FROM"
          + " documents";

  private static final RowMapper<Document> ROW_MAPPER =
      (rs, rowNum) ->
          new Document(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("document_type_id"),
              (UUID) rs.getObject("client_id"),
              (UUID) rs.getObject("current_version_id"),
              ReviewStatus.valueOf(rs.getString("status")));

  private final JdbcTemplate jdbcTemplate;

  public DocumentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** Document of the expediente (no holder attribution). */
  public UUID insert(UUID companyId, UUID caseId, UUID documentTypeId) {
    return insert(companyId, caseId, documentTypeId, null);
  }

  /** clientId non-null attributes the document to a case holder (V27, BRIKKA V2 I1). */
  public UUID insert(UUID companyId, UUID caseId, UUID documentTypeId, UUID clientId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO documents (company_id, case_id, document_type_id, client_id, status) VALUES"
            + " (?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        documentTypeId,
        clientId,
        ReviewStatus.PENDING.name());
  }

  public Optional<Document> findById(UUID id) {
    List<Document> documents = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return documents.stream().findFirst();
  }

  public List<Document> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(SELECT + " WHERE case_id = ?", ROW_MAPPER, caseId);
  }

  /**
   * Sprint 32: lets a generator (dossier/contract) find the single Document row it keeps adding
   * versions to for a given case + type, instead of creating a new row on every generation. Those
   * generators never attribute to a holder, so this deliberately ignores client_id.
   */
  public Optional<Document> findByCaseIdAndDocumentTypeId(UUID caseId, UUID documentTypeId) {
    return jdbcTemplate
        .query(
            SELECT + " WHERE case_id = ? AND document_type_id = ?",
            ROW_MAPPER,
            caseId,
            documentTypeId)
        .stream()
        .findFirst();
  }

  /**
   * BRIKKA V2 I1: every document of a given type on a case, used by CaseChecklistService to decide
   * whether a per-holder / per-case requirement is satisfied (an APPROVED document with the
   * matching client_id).
   */
  public List<Document> findAllByCaseIdAndDocumentTypeId(UUID caseId, UUID documentTypeId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? AND document_type_id = ?", ROW_MAPPER, caseId, documentTypeId);
  }

  public void setCurrentVersionAndStatus(UUID documentId, UUID versionId, ReviewStatus status) {
    jdbcTemplate.update(
        "UPDATE documents SET current_version_id = ?, status = ?, updated_at = now() WHERE id ="
            + " ?",
        versionId,
        status.name(),
        documentId);
  }

  public void updateStatus(UUID documentId, ReviewStatus status) {
    jdbcTemplate.update(
        "UPDATE documents SET status = ?, updated_at = now() WHERE id = ?",
        status.name(),
        documentId);
  }
}
