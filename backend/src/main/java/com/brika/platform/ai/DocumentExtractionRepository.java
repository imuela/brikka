package com.brika.platform.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DocumentExtractionRepository {

  private static final String SELECT =
      "SELECT id, company_id, document_version_id, status, provider, model, extracted_data,"
          + " confidence, validated_by, validated_at, created_at FROM document_extractions";

  private static final String SELECT_QUALIFIED =
      "SELECT de.id, de.company_id, de.document_version_id, de.status, de.provider, de.model,"
          + " de.extracted_data, de.confidence, de.validated_by, de.validated_at, de.created_at"
          + " FROM document_extractions de";

  private static final RowMapper<DocumentExtraction> ROW_MAPPER =
      (rs, rowNum) ->
          new DocumentExtraction(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("document_version_id"),
              rs.getString("status"),
              rs.getString("provider"),
              rs.getString("model"),
              rs.getString("extracted_data"),
              rs.getString("confidence"),
              (UUID) rs.getObject("validated_by"),
              rs.getTimestamp("validated_at") == null
                  ? null
                  : rs.getTimestamp("validated_at").toInstant(),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public DocumentExtractionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insertPending(UUID companyId, UUID documentVersionId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO document_extractions (company_id, document_version_id, status, provider,"
            + " model) VALUES (?, ?, 'PENDING', 'none', 'none') RETURNING id",
        UUID.class,
        companyId,
        documentVersionId);
  }

  public void applyResult(
      UUID id,
      String status,
      String provider,
      String model,
      String extractedDataJson,
      String confidenceJson) {
    jdbcTemplate.update(
        "UPDATE document_extractions SET status = ?, provider = ?, model = ?, extracted_data ="
            + " ?::jsonb, confidence = ?::jsonb WHERE id = ?",
        status,
        provider,
        model,
        extractedDataJson,
        confidenceJson,
        id);
  }

  public Optional<DocumentExtraction> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<DocumentExtraction> findAllByDocumentId(UUID documentId) {
    return jdbcTemplate.query(
        SELECT_QUALIFIED
            + " JOIN document_versions dv ON de.document_version_id = dv.id WHERE"
            + " dv.document_id = ? ORDER BY de.created_at DESC",
        ROW_MAPPER,
        documentId);
  }
}
