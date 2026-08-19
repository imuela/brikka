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
public class PortalRefreshTokenRepository {

  private static final String SELECT =
      "SELECT id, portal_account_id, family_id, token_hash, issued_at, expires_at, revoked_at,"
          + " replaced_by_token_id FROM portal_refresh_tokens";

  private static final RowMapper<PortalRefreshToken> ROW_MAPPER =
      PortalRefreshTokenRepository::mapRow;

  private final JdbcTemplate jdbcTemplate;

  PortalRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  UUID insert(UUID portalAccountId, UUID familyId, String tokenHash, Instant expiresAt) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO portal_refresh_tokens (portal_account_id, family_id, token_hash,"
            + " expires_at) VALUES (?, ?, ?, ?) RETURNING id",
        UUID.class,
        portalAccountId,
        familyId,
        tokenHash,
        Timestamp.from(expiresAt));
  }

  Optional<PortalRefreshToken> findByTokenHash(String tokenHash) {
    return jdbcTemplate.query(SELECT + " WHERE token_hash = ?", ROW_MAPPER, tokenHash).stream()
        .findFirst();
  }

  void markReplaced(UUID tokenId, UUID replacedByTokenId) {
    jdbcTemplate.update(
        "UPDATE portal_refresh_tokens SET revoked_at = now(), replaced_by_token_id = ?"
            + " WHERE id = ?",
        replacedByTokenId,
        tokenId);
  }

  void revokeFamily(UUID familyId) {
    jdbcTemplate.update(
        "UPDATE portal_refresh_tokens SET revoked_at = now() WHERE family_id = ? AND revoked_at"
            + " IS NULL",
        familyId);
  }

  void revokeAllForAccount(UUID portalAccountId) {
    jdbcTemplate.update(
        "UPDATE portal_refresh_tokens SET revoked_at = now() WHERE portal_account_id = ? AND"
            + " revoked_at IS NULL",
        portalAccountId);
  }

  private static PortalRefreshToken mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new PortalRefreshToken(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("portal_account_id"),
        (UUID) rs.getObject("family_id"),
        rs.getString("token_hash"),
        rs.getTimestamp("issued_at").toInstant(),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
        (UUID) rs.getObject("replaced_by_token_id"));
  }
}
