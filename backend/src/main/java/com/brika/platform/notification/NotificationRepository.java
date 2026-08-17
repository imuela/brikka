package com.brika.platform.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {

  private static final String SELECT =
      "SELECT id, company_id, recipient_user_id, recipient_client_id, type, payload, read_at,"
          + " created_at FROM notifications";

  private static final RowMapper<Notification> ROW_MAPPER =
      (rs, rowNum) ->
          new Notification(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("recipient_user_id"),
              (UUID) rs.getObject("recipient_client_id"),
              rs.getString("type"),
              rs.getString("payload"),
              rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public NotificationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Notification> findAllByRecipientClientId(UUID clientId) {
    return jdbcTemplate.query(
        SELECT + " WHERE recipient_client_id = ? ORDER BY created_at DESC", ROW_MAPPER, clientId);
  }

  /** ADR-NOTIF-001, Sprint 8: writer side — no producer existed before this sprint. */
  public UUID insert(
      UUID companyId,
      UUID recipientUserId,
      UUID recipientClientId,
      String type,
      String payloadJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO notifications (company_id, recipient_user_id, recipient_client_id, type,"
            + " payload) VALUES (?, ?, ?, ?, ?::jsonb) RETURNING id",
        UUID.class,
        companyId,
        recipientUserId,
        recipientClientId,
        type,
        payloadJson);
  }

  public Optional<Notification> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<Notification> findAllByRecipientUserId(UUID userId) {
    return jdbcTemplate.query(
        SELECT + " WHERE recipient_user_id = ? ORDER BY created_at DESC", ROW_MAPPER, userId);
  }

  public void markRead(UUID id) {
    jdbcTemplate.update(
        "UPDATE notifications SET read_at = now() WHERE id = ? AND read_at IS NULL", id);
  }
}
