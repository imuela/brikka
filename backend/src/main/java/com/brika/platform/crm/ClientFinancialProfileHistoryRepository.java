package com.brika.platform.crm;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClientFinancialProfileHistoryRepository {

  private static final String SELECT =
      "SELECT id, company_id, client_id, financial_profile_id, marital_status, dependents,"
          + " employment_type, contract_type, employer_name, years_employed, monthly_income,"
          + " savings, other_debts_monthly_payment, credit_card_debt, source, status,"
          + " evidence_document_version_id, changed_by, changed_at"
          + " FROM client_financial_profile_history";

  private static final RowMapper<ClientFinancialProfileHistoryEntry> ROW_MAPPER =
      (rs, rowNum) ->
          new ClientFinancialProfileHistoryEntry(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("client_id"),
              (UUID) rs.getObject("financial_profile_id"),
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
              (UUID) rs.getObject("changed_by"),
              rs.getTimestamp("changed_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public ClientFinancialProfileHistoryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insert(ClientFinancialProfile snapshot, UUID changedBy) {
    jdbcTemplate.update(
        "INSERT INTO client_financial_profile_history (company_id, client_id,"
            + " financial_profile_id, marital_status, dependents, employment_type, contract_type,"
            + " employer_name, years_employed, monthly_income, savings,"
            + " other_debts_monthly_payment, credit_card_debt, source, status,"
            + " evidence_document_version_id, changed_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?)",
        snapshot.companyId(),
        snapshot.clientId(),
        snapshot.id(),
        snapshot.maritalStatus(),
        snapshot.dependents(),
        snapshot.employmentType(),
        snapshot.contractType(),
        snapshot.employerName(),
        snapshot.yearsEmployed(),
        snapshot.monthlyIncome(),
        snapshot.savings(),
        snapshot.otherDebtsMonthlyPayment(),
        snapshot.creditCardDebt(),
        snapshot.source(),
        snapshot.status(),
        snapshot.evidenceDocumentVersionId(),
        changedBy);
  }

  public List<ClientFinancialProfileHistoryEntry> findAllByClientId(UUID clientId) {
    return jdbcTemplate.query(
        SELECT + " WHERE client_id = ? ORDER BY changed_at DESC", ROW_MAPPER, clientId);
  }
}
