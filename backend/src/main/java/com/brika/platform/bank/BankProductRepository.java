package com.brika.platform.bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Managed via BANK_UPDATE: no dedicated BANK_PRODUCT_* permission exists (Sprint 5 pre-flight
 * decision 11.2).
 */
@Repository
public class BankProductRepository {

  private static final String SELECT =
      "SELECT id, bank_id, code, name, status, metadata FROM bank_products";

  private static final RowMapper<BankProduct> ROW_MAPPER =
      (rs, rowNum) ->
          new BankProduct(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("bank_id"),
              rs.getString("code"),
              rs.getString("name"),
              rs.getString("status"),
              rs.getString("metadata"));

  private final JdbcTemplate jdbcTemplate;

  public BankProductRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID bankId, String code, String name, String metadataJson) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_products (bank_id, code, name, status, metadata) VALUES (?, ?, ?,"
            + " 'ACTIVE', ?::jsonb) RETURNING id",
        UUID.class,
        bankId,
        code,
        name,
        metadataJson == null ? "{}" : metadataJson);
  }

  public Optional<BankProduct> findById(UUID id) {
    List<BankProduct> products = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return products.stream().findFirst();
  }

  public List<BankProduct> findAllByBankId(UUID bankId) {
    return jdbcTemplate.query(SELECT + " WHERE bank_id = ? ORDER BY name", ROW_MAPPER, bankId);
  }

  public void update(UUID id, String name, String status, String metadataJson) {
    jdbcTemplate.update(
        "UPDATE bank_products SET name = ?, status = ?, metadata = ?::jsonb WHERE id = ?",
        name,
        status,
        metadataJson == null ? "{}" : metadataJson,
        id);
  }
}
