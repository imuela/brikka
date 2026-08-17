package com.brika.platform.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CompanyRepository {

  private static final String SELECT =
      "SELECT id, legal_name, trade_name, tax_id, status FROM companies";

  private static final RowMapper<Company> COMPANY_ROW_MAPPER = CompanyRepository::mapCompany;

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

  public Optional<Company> findById(UUID id) {
    List<Company> companies = jdbcTemplate.query(SELECT + " WHERE id = ?", COMPANY_ROW_MAPPER, id);
    return companies.stream().findFirst();
  }

  public List<Company> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY legal_name", COMPANY_ROW_MAPPER);
  }

  public void update(UUID id, String legalName, String tradeName, String taxId) {
    jdbcTemplate.update(
        "UPDATE companies SET legal_name = ?, trade_name = ?, tax_id = ?, updated_at = now()"
            + " WHERE id = ?",
        legalName,
        tradeName,
        taxId,
        id);
  }

  public void updateStatus(UUID id, String status) {
    jdbcTemplate.update(
        "UPDATE companies SET status = ?, updated_at = now() WHERE id = ?", status, id);
  }

  private static Company mapCompany(ResultSet rs, int rowNum) throws SQLException {
    return new Company(
        (UUID) rs.getObject("id"),
        rs.getString("legal_name"),
        rs.getString("trade_name"),
        rs.getString("tax_id"),
        rs.getString("status"));
  }
}
