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
public class PortalPasswordResetTokenRepository {

  private static final String SELECT =
      "SELECT id, portal_account_id, token_hash, expires_at, used_at FROM"
          + " portal_password_reset_tokens";

  private static final RowMapper<PortalPasswordResetToken> ROW_MAPPER =
      PortalPasswordResetTokenRepository::mapRow;

  private final JdbcTemplate jdbcTemplate;

  public PortalPasswordResetTokenRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  UUID insert(UUID portalAccountId, String tokenHash, Instant expiresAt) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO portal_password_reset_tokens (portal_account_id, token_hash, expires_at)"
            + " VALUES (?, ?, ?) RETURNING id",
        UUID.class,
        portalAccountId,
        tokenHash,
        Timestamp.from(expiresAt));
  }

  Optional<PortalPasswordResetToken> findByTokenHash(String tokenHash) {
    return jdbcTemplate.query(SELECT + " WHERE token_hash = ?", ROW_MAPPER, tokenHash).stream()
        .findFirst();
  }

  void markUsed(UUID id) {
    jdbcTemplate.update("UPDATE portal_password_reset_tokens SET used_at = now() WHERE id = ?", id);
  }

  private static PortalPasswordResetToken mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new PortalPasswordResetToken(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("portal_account_id"),
        rs.getString("token_hash"),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getTimestamp("used_at") == null ? null : rs.getTimestamp("used_at").toInstant());
  }
}
