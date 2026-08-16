package com.brika.platform.communication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationParticipantRepository {

  private static final String SELECT =
      "SELECT id, company_id, conversation_id, participant_user_id, participant_client_id,"
          + " created_at, removed_at FROM conversation_participants";

  private static final RowMapper<ConversationParticipant> ROW_MAPPER =
      (rs, rowNum) ->
          new ConversationParticipant(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("conversation_id"),
              (UUID) rs.getObject("participant_user_id"),
              (UUID) rs.getObject("participant_client_id"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("removed_at") == null
                  ? null
                  : rs.getTimestamp("removed_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public ConversationParticipantRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insertClientParticipant(UUID companyId, UUID conversationId, UUID clientId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO conversation_participants (company_id, conversation_id,"
            + " participant_client_id) VALUES (?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        conversationId,
        clientId);
  }

  public UUID insertUserParticipant(UUID companyId, UUID conversationId, UUID userId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO conversation_participants (company_id, conversation_id, participant_user_id)"
            + " VALUES (?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        conversationId,
        userId);
  }

  public Optional<ConversationParticipant> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<ConversationParticipant> findActiveByConversationId(UUID conversationId) {
    return jdbcTemplate.query(
        SELECT + " WHERE conversation_id = ? AND removed_at IS NULL ORDER BY created_at",
        ROW_MAPPER,
        conversationId);
  }

  public boolean hasActiveClientParticipant(UUID conversationId, UUID clientId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM conversation_participants WHERE conversation_id = ? AND"
                + " participant_client_id = ? AND removed_at IS NULL",
            Integer.class,
            conversationId,
            clientId);
    return count != null && count > 0;
  }

  public void remove(UUID id) {
    jdbcTemplate.update(
        "UPDATE conversation_participants SET removed_at = now() WHERE id = ? AND removed_at IS"
            + " NULL",
        id);
  }
}
