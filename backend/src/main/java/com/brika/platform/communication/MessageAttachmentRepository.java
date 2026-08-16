package com.brika.platform.communication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MessageAttachmentRepository {

  private static final String SELECT =
      "SELECT id, company_id, message_id, storage_key, original_filename, mime_type, size_bytes,"
          + " checksum, created_at FROM message_attachments";

  private static final RowMapper<MessageAttachment> ROW_MAPPER =
      (rs, rowNum) ->
          new MessageAttachment(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("message_id"),
              rs.getString("storage_key"),
              rs.getString("original_filename"),
              rs.getString("mime_type"),
              rs.getLong("size_bytes"),
              rs.getString("checksum"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public MessageAttachmentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID id,
      UUID companyId,
      UUID messageId,
      String storageKey,
      String originalFilename,
      String mimeType,
      long sizeBytes,
      String checksum) {
    jdbcTemplate.update(
        "INSERT INTO message_attachments (id, company_id, message_id, storage_key,"
            + " original_filename, mime_type, size_bytes, checksum) VALUES (?, ?, ?, ?, ?, ?, ?,"
            + " ?)",
        id,
        companyId,
        messageId,
        storageKey,
        originalFilename,
        mimeType,
        sizeBytes,
        checksum);
    return id;
  }

  public Optional<MessageAttachment> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<MessageAttachment> findAllByMessageId(UUID messageId) {
    return jdbcTemplate.query(
        SELECT + " WHERE message_id = ? ORDER BY created_at", ROW_MAPPER, messageId);
  }
}
