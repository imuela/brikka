package com.brika.platform.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Global catalog (ADR-DOC-001), not tenant-owned — management is GLOBAL-scoped SUPERADMIN-only,
 * read is GLOBAL for every internal role (ADR-RBAC-001). No rows are seeded: no approved document
 * defines which document_type is mandatory for which operation_type (Sprint 4 pre-flight review).
 */
@Repository
public class DocumentRequirementRepository {

  private static final String SELECT =
      "SELECT id, operation_type, document_type_id, mandatory, conditions, active FROM"
          + " document_requirements";

  private static final RowMapper<DocumentRequirement> ROW_MAPPER =
      (rs, rowNum) ->
          new DocumentRequirement(
              (UUID) rs.getObject("id"),
              rs.getString("operation_type"),
              (UUID) rs.getObject("document_type_id"),
              rs.getBoolean("mandatory"),
              rs.getString("conditions"),
              rs.getBoolean("active"));

  private final JdbcTemplate jdbcTemplate;

  public DocumentRequirementRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      String operationType, UUID documentTypeId, boolean mandatory, String conditionsJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO document_requirements (operation_type, document_type_id, mandatory,"
            + " conditions) VALUES (?, ?, ?, ?::jsonb) RETURNING id",
        UUID.class,
        operationType,
        documentTypeId,
        mandatory,
        conditionsJson == null ? "{}" : conditionsJson);
  }

  public Optional<DocumentRequirement> findById(UUID id) {
    List<DocumentRequirement> requirements =
        jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return requirements.stream().findFirst();
  }

  public List<DocumentRequirement> findAll(String operationTypeFilter) {
    if (operationTypeFilter == null || operationTypeFilter.isBlank()) {
      return jdbcTemplate.query(SELECT + " ORDER BY operation_type", ROW_MAPPER);
    }
    return jdbcTemplate.query(
        SELECT + " WHERE operation_type = ? ORDER BY operation_type",
        ROW_MAPPER,
        operationTypeFilter);
  }

  /**
   * BRIKKA V2 I1: the active checklist for an operation type — feeds both the auto-generation of
   * document_requests on entering DOCUMENTATION and the checklist view.
   */
  public List<DocumentRequirement> findActiveByOperationType(String operationType) {
    return jdbcTemplate.query(
        SELECT + " WHERE operation_type = ? AND active = true ORDER BY mandatory DESC, id",
        ROW_MAPPER,
        operationType);
  }

  public void update(UUID id, boolean mandatory, boolean active, String conditionsJson) {
    jdbcTemplate.update(
        "UPDATE document_requirements SET mandatory = ?, active = ?, conditions = ?::jsonb,"
            + " updated_at = now() WHERE id = ?",
        mandatory,
        active,
        conditionsJson == null ? "{}" : conditionsJson,
        id);
  }
}
