package com.brika.platform.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentVersionRepository {

  private static final String SELECT =
      "SELECT id, document_id, version_number, storage_key, original_filename, mime_type,"
          + " size_bytes, checksum, uploaded_by, uploaded_by_client_id, uploaded_at,"
          + " review_status, reviewed_by, reviewed_at, review_comment FROM document_versions";

  private static final RowMapper<DocumentVersion> ROW_MAPPER =
      (rs, rowNum) ->
          new DocumentVersion(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("document_id"),
              rs.getInt("version_number"),
              rs.getString("storage_key"),
              rs.getString("original_filename"),
              rs.getString("mime_type"),
              rs.getLong("size_bytes"),
              rs.getString("checksum"),
              (UUID) rs.getObject("uploaded_by"),
              (UUID) rs.getObject("uploaded_by_client_id"),
              rs.getTimestamp("uploaded_at").toInstant(),
              ReviewStatus.valueOf(rs.getString("review_status")),
              (UUID) rs.getObject("reviewed_by"),
              rs.getTimestamp("reviewed_at") == null
                  ? null
                  : rs.getTimestamp("reviewed_at").toInstant(),
              rs.getString("review_comment"));

  private final JdbcTemplate jdbcTemplate;

  public DocumentVersionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insert(
      UUID id,
      UUID documentId,
      int versionNumber,
      String storageKey,
      String originalFilename,
      String mimeType,
      long sizeBytes,
      String checksum,
      UUID uploadedBy) {
    jdbcTemplate.update(
        "INSERT INTO document_versions (id, document_id, version_number, storage_key,"
            + " original_filename, mime_type, size_bytes, checksum, uploaded_by, review_status)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        documentId,
        versionNumber,
        storageKey,
        originalFilename,
        mimeType,
        sizeBytes,
        checksum,
        uploadedBy,
        ReviewStatus.PENDING.name());
  }

  /** Portal Cliente upload (Sprint 7, V12): mirrors {@link #insert}, but for a client uploader. */
  public void insertFromClient(
      UUID id,
      UUID documentId,
      int versionNumber,
      String storageKey,
      String originalFilename,
      String mimeType,
      long sizeBytes,
      String checksum,
      UUID uploadedByClientId) {
    jdbcTemplate.update(
        "INSERT INTO document_versions (id, document_id, version_number, storage_key,"
            + " original_filename, mime_type, size_bytes, checksum, uploaded_by_client_id,"
            + " review_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        id,
        documentId,
        versionNumber,
        storageKey,
        originalFilename,
        mimeType,
        sizeBytes,
        checksum,
        uploadedByClientId,
        ReviewStatus.PENDING.name());
  }

  public Optional<DocumentVersion> findById(UUID id) {
    List<DocumentVersion> versions = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return versions.stream().findFirst();
  }

  public List<DocumentVersion> findAllByDocumentId(UUID documentId) {
    return jdbcTemplate.query(
        SELECT + " WHERE document_id = ? ORDER BY version_number DESC", ROW_MAPPER, documentId);
  }

  public int nextVersionNumber(UUID documentId) {
    Integer max =
        jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version_number), 0) FROM document_versions WHERE document_id = ?",
            Integer.class,
            documentId);
    return (max == null ? 0 : max) + 1;
  }

  public void review(UUID id, ReviewStatus status, UUID reviewedBy, String comment) {
    jdbcTemplate.update(
        "UPDATE document_versions SET review_status = ?, reviewed_by = ?, reviewed_at = now(),"
            + " review_comment = ? WHERE id = ?",
        status.name(),
        reviewedBy,
        comment,
        id);
  }
}
