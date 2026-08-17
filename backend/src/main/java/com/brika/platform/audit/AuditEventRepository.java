package com.brika.platform.audit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** ADR-AUDIT-001 / Sprint 11: audit_events is append-only — no update/delete method exists. */
@Repository
public class AuditEventRepository {

  private static final String SELECT =
      "SELECT id, company_id, actor_user_id, actor_client_id, action, resource_type, resource_id,"
          + " request_id, metadata, created_at FROM audit_events";

  private static final RowMapper<AuditEvent> ROW_MAPPER =
      (rs, rowNum) ->
          new AuditEvent(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("actor_user_id"),
              (UUID) rs.getObject("actor_client_id"),
              rs.getString("action"),
              rs.getString("resource_type"),
              (UUID) rs.getObject("resource_id"),
              rs.getString("request_id"),
              rs.getString("metadata"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public AuditEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID actorUserId,
      UUID actorClientId,
      String action,
      String resourceType,
      UUID resourceId,
      String requestId,
      String metadataJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO audit_events (company_id, actor_user_id, actor_client_id, action,"
            + " resource_type, resource_id, request_id, metadata) VALUES (?, ?, ?, ?, ?, ?, ?,"
            + " ?::jsonb) RETURNING id",
        UUID.class,
        companyId,
        actorUserId,
        actorClientId,
        action,
        resourceType,
        resourceId,
        requestId,
        metadataJson);
  }

  public List<AuditEvent> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY created_at DESC", ROW_MAPPER);
  }

  public Optional<AuditEvent> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }
}
