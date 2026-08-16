package com.brika.platform.bank;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BankCriteriaVersionRepository {

  private static final String SELECT =
      "SELECT id, bank_id, version, status, effective_from, effective_to, rules FROM"
          + " bank_criteria_versions";

  private static final RowMapper<BankCriteriaVersion> ROW_MAPPER =
      (rs, rowNum) ->
          new BankCriteriaVersion(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("bank_id"),
              rs.getString("version"),
              rs.getString("status"),
              rs.getTimestamp("effective_from").toInstant(),
              rs.getTimestamp("effective_to") == null
                  ? null
                  : rs.getTimestamp("effective_to").toInstant(),
              rs.getString("rules"));

  private final JdbcTemplate jdbcTemplate;

  public BankCriteriaVersionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID bankId, String version, Instant effectiveFrom, Instant effectiveTo, String rulesJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_criteria_versions (bank_id, version, status, effective_from,"
            + " effective_to, rules) VALUES (?, ?, 'ACTIVE', ?, ?, ?::jsonb) RETURNING id",
        UUID.class,
        bankId,
        version,
        Timestamp.from(effectiveFrom == null ? Instant.now() : effectiveFrom),
        effectiveTo == null ? null : Timestamp.from(effectiveTo),
        rulesJson == null ? "{}" : rulesJson);
  }

  public Optional<BankCriteriaVersion> findById(UUID id) {
    List<BankCriteriaVersion> versions =
        jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return versions.stream().findFirst();
  }

  public List<BankCriteriaVersion> findAllByBankId(UUID bankId) {
    return jdbcTemplate.query(
        SELECT + " WHERE bank_id = ? ORDER BY effective_from DESC", ROW_MAPPER, bankId);
  }

  public void update(UUID id, String status, Instant effectiveTo, String rulesJson) {
    jdbcTemplate.update(
        "UPDATE bank_criteria_versions SET status = ?, effective_to = ?, rules = ?::jsonb WHERE"
            + " id = ?",
        status,
        effectiveTo == null ? null : Timestamp.from(effectiveTo),
        rulesJson == null ? "{}" : rulesJson,
        id);
  }
}
