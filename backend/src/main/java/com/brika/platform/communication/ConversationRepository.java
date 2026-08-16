package com.brika.platform.communication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * conversations.status has no documented catalog anywhere. Defaulted once to 'ACTIVE' at creation,
 * server-controlled, no endpoint changes it in Sprint 7 — same conservative-default discipline as
 * every other undocumented status column in this codebase.
 */
@Repository
public class ConversationRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, type, status, created_at, updated_at FROM conversations";

  private static final RowMapper<Conversation> ROW_MAPPER =
      (rs, rowNum) ->
          new Conversation(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              rs.getString("type"),
              rs.getString("status"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public ConversationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID companyId, UUID caseId, String type) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO conversations (company_id, case_id, type, status) VALUES (?, ?, ?, 'ACTIVE')"
            + " RETURNING id",
        UUID.class,
        companyId,
        caseId,
        type);
  }

  public Optional<Conversation> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<Conversation> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY created_at DESC", ROW_MAPPER, caseId);
  }
}
