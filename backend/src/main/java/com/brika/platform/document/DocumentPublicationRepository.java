package com.brika.platform.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentPublicationRepository {

  private static final String SELECT =
      "SELECT id, company_id, document_id, document_version_id, published_to_portal,"
          + " published_by, published_at, revoked_at FROM document_publications";

  private static final RowMapper<DocumentPublication> ROW_MAPPER =
      (rs, rowNum) ->
          new DocumentPublication(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("document_id"),
              (UUID) rs.getObject("document_version_id"),
              rs.getBoolean("published_to_portal"),
              (UUID) rs.getObject("published_by"),
              rs.getTimestamp("published_at").toInstant(),
              rs.getTimestamp("revoked_at") == null
                  ? null
                  : rs.getTimestamp("revoked_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public DocumentPublicationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID companyId, UUID documentId, UUID documentVersionId, UUID publishedBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO document_publications (company_id, document_id, document_version_id,"
            + " published_to_portal, published_by) VALUES (?, ?, ?, true, ?) RETURNING id",
        UUID.class,
        companyId,
        documentId,
        documentVersionId,
        publishedBy);
  }

  /**
   * At most one row with revoked_at IS NULL per document in practice (publish revokes prior actives
   * first).
   */
  public Optional<DocumentPublication> findActiveByDocumentId(UUID documentId) {
    List<DocumentPublication> publications =
        jdbcTemplate.query(
            SELECT + " WHERE document_id = ? AND revoked_at IS NULL ORDER BY published_at DESC",
            ROW_MAPPER,
            documentId);
    return publications.stream().findFirst();
  }

  public void revokeActive(UUID documentId) {
    jdbcTemplate.update(
        "UPDATE document_publications SET revoked_at = now() WHERE document_id = ? AND"
            + " revoked_at IS NULL",
        documentId);
  }
}
