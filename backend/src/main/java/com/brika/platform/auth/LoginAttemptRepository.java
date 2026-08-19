package com.brika.platform.auth;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Pure technical infrastructure (a realm tag + an identifier string + a timestamp, no identity
 * resolution) — shared by both the internal and Portal login flows, the same way CORS configuration
 * already is in SecurityConfig. This does not blur ADR-PORTAL-AUTH-001: it never resolves a `User`
 * or a `ClientPortalAccount`, only counts attempts.
 */
@Repository
public class LoginAttemptRepository {

  private final JdbcTemplate jdbcTemplate;

  LoginAttemptRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  void record(String realm, String identifier, boolean succeeded) {
    jdbcTemplate.update(
        "INSERT INTO login_attempts (realm, identifier, succeeded) VALUES (?, ?, ?)",
        realm,
        identifier,
        succeeded);
  }

  int countFailedSince(String realm, String identifier, Instant since) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM login_attempts WHERE realm = ? AND identifier = ? AND"
                + " succeeded = false AND attempted_at >= ?",
            Integer.class,
            realm,
            identifier,
            Timestamp.from(since));
    return count == null ? 0 : count;
  }
}
