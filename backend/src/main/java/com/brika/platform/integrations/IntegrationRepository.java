package com.brika.platform.integrations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * ADR-INTEGRATIONS-001: read-only in V1 — no insert/update method exists because no endpoint writes
 * to this table (credentials_ref is opaque and never dereferenced here).
 */
@Repository
public class IntegrationRepository {

  private static final String SELECT =
      "SELECT id, company_id, type, status, config, credentials_ref, created_at, updated_at"
          + " FROM integrations";

  private static final RowMapper<Integration> ROW_MAPPER =
      (rs, rowNum) ->
          new Integration(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              rs.getString("type"),
              rs.getString("status"),
              rs.getString("config"),
              rs.getString("credentials_ref"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public IntegrationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Integration> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY created_at DESC", ROW_MAPPER);
  }

  public Optional<Integration> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }
}
