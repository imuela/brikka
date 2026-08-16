package com.brika.platform.plan;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CompanySubscriptionRepository {

  private final JdbcTemplate jdbcTemplate;

  public CompanySubscriptionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID companyId, UUID planId, String status) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO company_subscriptions (company_id, plan_id, status) VALUES (?, ?, ?)"
            + " RETURNING id",
        UUID.class,
        companyId,
        planId,
        status);
  }

  public CompanySubscription findByCompanyId(UUID companyId) {
    return jdbcTemplate.queryForObject(
        "SELECT id, company_id, plan_id, status FROM company_subscriptions WHERE company_id = ?",
        (rs, rowNum) ->
            new CompanySubscription(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("company_id"),
                (UUID) rs.getObject("plan_id"),
                rs.getString("status")),
        companyId);
  }
}
