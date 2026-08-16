package com.brika.platform.document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Global catalog, already seeded in V2 (FUNCTIONAL_SPECIFICATION.md §11 examples) — read-only here.
 */
@Repository
public class DocumentTypeRepository {

  private static final RowMapper<DocumentType> ROW_MAPPER =
      (rs, rowNum) ->
          new DocumentType(
              (UUID) rs.getObject("id"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getBoolean("active"));

  private final JdbcTemplate jdbcTemplate;

  public DocumentTypeRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<DocumentType> findAll() {
    return jdbcTemplate.query(
        "SELECT id, code, name, active FROM document_types ORDER BY name", ROW_MAPPER);
  }

  public Optional<DocumentType> findById(UUID id) {
    List<DocumentType> types =
        jdbcTemplate.query(
            "SELECT id, code, name, active FROM document_types WHERE id = ?", ROW_MAPPER, id);
    return types.stream().findFirst();
  }
}
