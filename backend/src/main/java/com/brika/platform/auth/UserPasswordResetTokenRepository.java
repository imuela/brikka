package com.brika.platform.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserPasswordResetTokenRepository {

  private static final String SELECT =
      "SELECT id, user_id, token_hash, expires_at, used_at FROM user_password_reset_tokens";

  private static final RowMapper<UserPasswordResetToken> ROW_MAPPER =
      UserPasswordResetTokenRepository::mapRow;

  private final JdbcTemplate jdbcTemplate;

  public UserPasswordResetTokenRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  UUID insert(UUID userId, String tokenHash, Instant expiresAt) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO user_password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?,"
            + " ?) RETURNING id",
        UUID.class,
        userId,
        tokenHash,
        Timestamp.from(expiresAt));
  }

  Optional<UserPasswordResetToken> findByTokenHash(String tokenHash) {
    return jdbcTemplate.query(SELECT + " WHERE token_hash = ?", ROW_MAPPER, tokenHash).stream()
        .findFirst();
  }

  void markUsed(UUID id) {
    jdbcTemplate.update("UPDATE user_password_reset_tokens SET used_at = now() WHERE id = ?", id);
  }

  private static UserPasswordResetToken mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new UserPasswordResetToken(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("user_id"),
        rs.getString("token_hash"),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant());
  }
}
