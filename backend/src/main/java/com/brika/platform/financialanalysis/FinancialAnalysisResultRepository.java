package com.brika.platform.financialanalysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FinancialAnalysisResultRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, client_id, principal, interest_rate, term_months,"
          + " monthly_payment, monthly_income, existing_monthly_debts, dti_percent,"
          + " viability_category, quota_source, quota_source_id, rules_version, explanation,"
          + " calculated_by, calculated_at FROM case_financial_analysis_results";

  private static final RowMapper<FinancialAnalysisResult> ROW_MAPPER =
      (rs, rowNum) ->
          new FinancialAnalysisResult(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("client_id"),
              rs.getBigDecimal("principal"),
              rs.getBigDecimal("interest_rate"),
              rs.getInt("term_months"),
              rs.getBigDecimal("monthly_payment"),
              rs.getBigDecimal("monthly_income"),
              rs.getBigDecimal("existing_monthly_debts"),
              rs.getBigDecimal("dti_percent"),
              rs.getString("viability_category"),
              rs.getString("quota_source"),
              (UUID) rs.getObject("quota_source_id"),
              rs.getString("rules_version"),
              rs.getString("explanation"),
              (UUID) rs.getObject("calculated_by"),
              rs.getTimestamp("calculated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public FinancialAnalysisResultRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID caseId,
      UUID clientId,
      BigDecimal principal,
      BigDecimal interestRate,
      int termMonths,
      BigDecimal monthlyPayment,
      BigDecimal monthlyIncome,
      BigDecimal existingMonthlyDebts,
      BigDecimal dtiPercent,
      String viabilityCategory,
      String quotaSource,
      UUID quotaSourceId,
      String rulesVersion,
      String explanationJson,
      UUID calculatedBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO case_financial_analysis_results (company_id, case_id, client_id, principal,"
            + " interest_rate, term_months, monthly_payment, monthly_income,"
            + " existing_monthly_debts, dti_percent, viability_category, quota_source,"
            + " quota_source_id, rules_version, explanation, calculated_by) VALUES (?, ?, ?, ?, ?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        clientId,
        principal,
        interestRate,
        termMonths,
        monthlyPayment,
        monthlyIncome,
        existingMonthlyDebts,
        dtiPercent,
        viabilityCategory,
        quotaSource,
        quotaSourceId,
        rulesVersion,
        explanationJson,
        calculatedBy);
  }

  public Optional<FinancialAnalysisResult> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<FinancialAnalysisResult> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY calculated_at DESC", ROW_MAPPER, caseId);
  }
}
