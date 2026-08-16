package com.brika.platform.identity;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CompanyRepository {

  private final JdbcTemplate jdbcTemplate;

  public CompanyRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(String legalName, String tradeName, String taxId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO companies (legal_name, trade_name, tax_id, status) VALUES (?, ?, ?,"
            + " 'ACTIVE') RETURNING id",
        UUID.class,
        legalName,
        tradeName,
        taxId);
  }
}
