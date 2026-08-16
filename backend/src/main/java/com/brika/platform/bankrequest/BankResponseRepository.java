package com.brika.platform.bankrequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * bank_responses.status: same absence of a documented catalog as bank_requests.status. Defaulted
 * once at creation to 'RECEIVED', server-controlled, no endpoint changes it in Sprint 6A.
 */
@Repository
public class BankResponseRepository {

  private static final String SELECT =
      "SELECT id, bank_request_id, status, received_at, summary, payload, created_at FROM"
          + " bank_responses";

  private static final RowMapper<BankResponse> ROW_MAPPER =
      (rs, rowNum) ->
          new BankResponse(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("bank_request_id"),
              rs.getString("status"),
              rs.getTimestamp("received_at").toInstant(),
              rs.getString("summary"),
              rs.getString("payload"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public BankResponseRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID bankRequestId, String summary, String payloadJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_responses (bank_request_id, status, received_at, summary, payload)"
            + " VALUES (?, 'RECEIVED', now(), ?, ?::jsonb) RETURNING id",
        UUID.class,
        bankRequestId,
        summary,
        payloadJson);
  }

  public Optional<BankResponse> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<BankResponse> findAllByBankRequestId(UUID bankRequestId) {
    return jdbcTemplate.query(
        SELECT + " WHERE bank_request_id = ? ORDER BY created_at DESC", ROW_MAPPER, bankRequestId);
  }
}
