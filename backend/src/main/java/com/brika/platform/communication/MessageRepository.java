package com.brika.platform.communication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MessageRepository {

  private static final String SELECT =
      "SELECT id, conversation_id, sender_user_id, sender_client_id, body, created_at, edited_at"
          + " FROM messages";

  private static final RowMapper<Message> ROW_MAPPER =
      (rs, rowNum) ->
          new Message(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("conversation_id"),
              (UUID) rs.getObject("sender_user_id"),
              (UUID) rs.getObject("sender_client_id"),
              rs.getString("body"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("edited_at") == null
                  ? null
                  : rs.getTimestamp("edited_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public MessageRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insertFromUser(UUID conversationId, UUID senderUserId, String body) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO messages (conversation_id, sender_user_id, body) VALUES (?, ?, ?) RETURNING"
            + " id",
        UUID.class,
        conversationId,
        senderUserId,
        body);
  }

  public UUID insertFromClient(UUID conversationId, UUID senderClientId, String body) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO messages (conversation_id, sender_client_id, body) VALUES (?, ?, ?)"
            + " RETURNING id",
        UUID.class,
        conversationId,
        senderClientId,
        body);
  }

  public Optional<Message> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<Message> findAllByConversationId(UUID conversationId) {
    return jdbcTemplate.query(
        SELECT + " WHERE conversation_id = ? ORDER BY created_at", ROW_MAPPER, conversationId);
  }
}
