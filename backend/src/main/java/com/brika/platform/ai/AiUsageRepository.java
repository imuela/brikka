package com.brika.platform.ai;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AiUsageRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, user_id, provider, model, operation, input_tokens,"
          + " output_tokens, estimated_cost, created_at FROM ai_usage";

  private static final RowMapper<AiUsage> ROW_MAPPER =
      (rs, rowNum) ->
          new AiUsage(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("user_id"),
              rs.getString("provider"),
              rs.getString("model"),
              rs.getString("operation"),
              (Integer) rs.getObject("input_tokens"),
              (Integer) rs.getObject("output_tokens"),
              rs.getBigDecimal("estimated_cost"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public AiUsageRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID caseId,
      UUID userId,
      String provider,
      String model,
      String operation,
      Integer inputTokens,
      Integer outputTokens,
      BigDecimal estimatedCost) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO ai_usage (company_id, case_id, user_id, provider, model, operation,"
            + " input_tokens, output_tokens, estimated_cost) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            + " RETURNING id",
        UUID.class,
        companyId,
        caseId,
        userId,
        provider,
        model,
        operation,
        inputTokens,
        outputTokens,
        estimatedCost);
  }

  public List<AiUsage> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        SELECT + " WHERE case_id = ? ORDER BY created_at DESC", ROW_MAPPER, caseId);
  }
}
