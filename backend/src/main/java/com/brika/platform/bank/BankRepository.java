package com.brika.platform.bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Global catalog, not tenant-owned. No rows are seeded (Sprint 5 pre-flight review): no approved
 * document names real banks/products/criteria — model and endpoints only.
 */
@Repository
public class BankRepository {

  private static final String SELECT = "SELECT id, code, name, status, metadata FROM banks";

  private static final RowMapper<Bank> ROW_MAPPER =
      (rs, rowNum) ->
          new Bank(
              (UUID) rs.getObject("id"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getString("status"),
              rs.getString("metadata"));

  private final JdbcTemplate jdbcTemplate;

  public BankRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(String code, String name, String metadataJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO banks (code, name, status, metadata) VALUES (?, ?, 'ACTIVE', ?::jsonb)"
            + " RETURNING id",
        UUID.class,
        code,
        name,
        metadataJson == null ? "{}" : metadataJson);
  }

  public Optional<Bank> findById(UUID id) {
    List<Bank> banks = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return banks.stream().findFirst();
  }

  public List<Bank> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY name", ROW_MAPPER);
  }

  public void update(UUID id, String name, String status, String metadataJson) {
    jdbcTemplate.update(
        "UPDATE banks SET name = ?, status = ?, metadata = ?::jsonb, updated_at = now() WHERE id"
            + " = ?",
        name,
        status,
        metadataJson == null ? "{}" : metadataJson,
        id);
  }
}
