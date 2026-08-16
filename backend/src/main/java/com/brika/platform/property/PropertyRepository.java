package com.brika.platform.property;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** properties.case_id is UNIQUE (CASE 1 ── 0..1 PROPERTY, 15_DEFINITIVE_ERD.md §PROPERTY). */
@Repository
public class PropertyRepository {

  private static final RowMapper<Property> ROW_MAPPER =
      (rs, rowNum) ->
          new Property(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              rs.getString("address"),
              rs.getString("property_type"),
              rs.getBigDecimal("valuation"),
              rs.getBigDecimal("purchase_price"));

  private final JdbcTemplate jdbcTemplate;

  public PropertyRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Property upsert(
      UUID companyId,
      UUID caseId,
      String addressJson,
      String propertyType,
      BigDecimal valuation,
      BigDecimal purchasePrice) {
    jdbcTemplate.update(
        "INSERT INTO properties (company_id, case_id, address, property_type, valuation,"
            + " purchase_price) VALUES (?, ?, ?::jsonb, ?, ?, ?) ON CONFLICT (case_id) DO UPDATE"
            + " SET address = EXCLUDED.address, property_type = EXCLUDED.property_type, valuation"
            + " = EXCLUDED.valuation, purchase_price = EXCLUDED.purchase_price, updated_at ="
            + " now()",
        companyId,
        caseId,
        addressJson,
        propertyType,
        valuation,
        purchasePrice);
    return findByCaseId(caseId).orElseThrow();
  }

  public Optional<Property> findByCaseId(UUID caseId) {
    List<Property> properties =
        jdbcTemplate.query(
            "SELECT id, company_id, case_id, address, property_type, valuation, purchase_price"
                + " FROM properties WHERE case_id = ?",
            ROW_MAPPER,
            caseId);
    return properties.stream().findFirst();
  }
}
