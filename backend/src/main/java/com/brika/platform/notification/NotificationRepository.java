package com.brika.platform.notification;

import java.util.List;
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
}
