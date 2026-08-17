package com.brika.platform.notification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationDeliveryRepository {

  private static final String SELECT =
      "SELECT id, notification_id, channel, status, provider_reference, sent_at, failed_reason,"
          + " created_at FROM notification_deliveries";

  private static final RowMapper<NotificationDelivery> ROW_MAPPER =
      (rs, rowNum) ->
          new NotificationDelivery(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("notification_id"),
              rs.getString("channel"),
              rs.getString("status"),
              rs.getString("provider_reference"),
              rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(),
              rs.getString("failed_reason"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public NotificationDeliveryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID notificationId,
      String channel,
      String status,
      String providerReference,
      Instant sentAt,
      String failedReason) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO notification_deliveries (notification_id, channel, status,"
            + " provider_reference, sent_at, failed_reason) VALUES (?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        notificationId,
        channel,
        status,
        providerReference,
        sentAt == null ? null : java.sql.Timestamp.from(sentAt),
        failedReason);
  }

  public List<NotificationDelivery> findAllByNotificationId(UUID notificationId) {
    return jdbcTemplate.query(
        SELECT + " WHERE notification_id = ? ORDER BY created_at", ROW_MAPPER, notificationId);
  }
}
