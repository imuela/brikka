package com.brika.platform.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PortalAccountCredentialRepository {

  private final JdbcTemplate jdbcTemplate;

  public PortalAccountCredentialRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<String> findPasswordHash(UUID portalAccountId) {
    return jdbcTemplate
        .query(
            "SELECT password_hash FROM portal_account_credentials WHERE portal_account_id = ?",
            (rs, rowNum) -> rs.getString("password_hash"),
            portalAccountId)
        .stream()
        .findFirst();
  }

  public boolean exists(UUID portalAccountId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM portal_account_credentials WHERE portal_account_id = ?",
            Integer.class,
            portalAccountId);
    return count != null && count > 0;
  }

  public void upsert(UUID portalAccountId, String passwordHash) {
    jdbcTemplate.update(
        "INSERT INTO portal_account_credentials (portal_account_id, password_hash) VALUES (?, ?)"
            + " ON CONFLICT (portal_account_id) DO UPDATE SET password_hash ="
            + " EXCLUDED.password_hash, updated_at = now()",
        portalAccountId,
        passwordHash);
  }
}
