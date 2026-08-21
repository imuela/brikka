package com.brika.platform.crm;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClientFinancialProfileRepository {

  private static final String SELECT =
      "SELECT id, company_id, client_id, marital_status, dependents, employment_type,"
          + " contract_type, employer_name, years_employed, monthly_income, savings,"
          + " other_debts_monthly_payment, credit_card_debt, source, status,"
          + " evidence_document_version_id, updated_by, created_at, updated_at"
          + " FROM client_financial_profiles";

  private static final RowMapper<ClientFinancialProfile> ROW_MAPPER =
      (rs, rowNum) ->
          new ClientFinancialProfile(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("client_id"),
              rs.getString("marital_status"),
              (Integer) rs.getObject("dependents"),
              rs.getString("employment_type"),
              rs.getString("contract_type"),
              rs.getString("employer_name"),
              (Integer) rs.getObject("years_employed"),
              rs.getBigDecimal("monthly_income"),
              rs.getBigDecimal("savings"),
              rs.getBigDecimal("other_debts_monthly_payment"),
              rs.getBigDecimal("credit_card_debt"),
              rs.getString("source"),
              rs.getString("status"),
              (UUID) rs.getObject("evidence_document_version_id"),
              (UUID) rs.getObject("updated_by"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public ClientFinancialProfileRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<ClientFinancialProfile> findByClientId(UUID clientId) {
    return jdbcTemplate.query(SELECT + " WHERE client_id = ?", ROW_MAPPER, clientId).stream()
        .findFirst();
  }

  public UUID insert(
      UUID companyId,
      UUID clientId,
      String maritalStatus,
      Integer dependents,
      String employmentType,
      String contractType,
      String employerName,
      Integer yearsEmployed,
      BigDecimal monthlyIncome,
      BigDecimal savings,
      BigDecimal otherDebtsMonthlyPayment,
      BigDecimal creditCardDebt,
      String source,
      String status,
      UUID evidenceDocumentVersionId,
      UUID updatedBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO client_financial_profiles (company_id, client_id, marital_status,"
            + " dependents, employment_type, contract_type, employer_name, years_employed,"
            + " monthly_income, savings, other_debts_monthly_payment, credit_card_debt, source,"
            + " status, evidence_document_version_id, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        clientId,
        maritalStatus,
        dependents,
        employmentType,
        contractType,
        employerName,
        yearsEmployed,
        monthlyIncome,
        savings,
        otherDebtsMonthlyPayment,
        creditCardDebt,
        source,
        status,
        evidenceDocumentVersionId,
        updatedBy);
  }

  public void update(
      UUID id,
      String maritalStatus,
      Integer dependents,
      String employmentType,
      String contractType,
      String employerName,
      Integer yearsEmployed,
      BigDecimal monthlyIncome,
      BigDecimal savings,
      BigDecimal otherDebtsMonthlyPayment,
      BigDecimal creditCardDebt,
      String source,
      String status,
      UUID evidenceDocumentVersionId,
      UUID updatedBy) {
    jdbcTemplate.update(
        "UPDATE client_financial_profiles SET marital_status = ?, dependents = ?,"
            + " employment_type = ?, contract_type = ?, employer_name = ?, years_employed = ?,"
            + " monthly_income = ?, savings = ?, other_debts_monthly_payment = ?,"
            + " credit_card_debt = ?, source = ?, status = ?, evidence_document_version_id = ?,"
            + " updated_by = ?, updated_at = now() WHERE id = ?",
        maritalStatus,
        dependents,
        employmentType,
        contractType,
        employerName,
        yearsEmployed,
        monthlyIncome,
        savings,
        otherDebtsMonthlyPayment,
        creditCardDebt,
        source,
        status,
        evidenceDocumentVersionId,
        updatedBy,
        id);
  }

  /**
   * Tenant safety check for a caller-supplied evidence document version: resolves the company that
   * actually owns it via document_versions -> documents, without requiring case access here (the
   * financial profile is a client-level, not case-level, resource).
   */
  public Optional<UUID> resolveDocumentVersionCompanyId(UUID documentVersionId) {
    List<UUID> rows =
        jdbcTemplate.query(
            "SELECT d.company_id FROM document_versions dv JOIN documents d ON d.id ="
                + " dv.document_id WHERE dv.id = ?",
            (rs, rowNum) -> (UUID) rs.getObject("company_id"),
            documentVersionId);
    return rows.stream().findFirst();
  }
}
