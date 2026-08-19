package com.brika.platform.auth;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserCredentialRepository {

  private final JdbcTemplate jdbcTemplate;

  public UserCredentialRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<String> findPasswordHash(UUID userId) {
    return jdbcTemplate
        .query(
            "SELECT password_hash FROM user_credentials WHERE user_id = ?",
            (rs, rowNum) -> rs.getString("password_hash"),
            userId)
        .stream()
        .findFirst();
  }

  public boolean exists(UUID userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_credentials WHERE user_id = ?", Integer.class, userId);
    return count != null && count > 0;
  }

  public void upsert(UUID userId, String passwordHash) {
    jdbcTemplate.update(
        "INSERT INTO user_credentials (user_id, password_hash) VALUES (?, ?)"
            + " ON CONFLICT (user_id) DO UPDATE SET password_hash = EXCLUDED.password_hash,"
            + " updated_at = now()",
        userId,
        passwordHash);
  }
}
