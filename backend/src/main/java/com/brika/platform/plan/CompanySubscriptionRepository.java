package com.brika.platform.plan;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CompanySubscriptionRepository {

  private static final String SELECT =
      "SELECT id, company_id, plan_id, status FROM company_subscriptions";

  private static final RowMapper<CompanySubscription> SUBSCRIPTION_ROW_MAPPER =
      CompanySubscriptionRepository::mapSubscription;

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

  public Optional<CompanySubscription> findByCompanyId(UUID companyId) {
    List<CompanySubscription> subscriptions =
        jdbcTemplate.query(SELECT + " WHERE company_id = ?", SUBSCRIPTION_ROW_MAPPER, companyId);
    return subscriptions.stream().findFirst();
  }

  public void updatePlanAndStatus(UUID companyId, UUID planId, String status) {
    jdbcTemplate.update(
        "UPDATE company_subscriptions SET plan_id = ?, status = ?, updated_at = now() WHERE"
            + " company_id = ?",
        planId,
        status,
        companyId);
  }

  public void cancel(UUID companyId) {
    jdbcTemplate.update(
        "UPDATE company_subscriptions SET status = 'CANCELLED', cancelled_at = now(),"
            + " updated_at = now() WHERE company_id = ?",
        companyId);
  }

  private static CompanySubscription mapSubscription(ResultSet rs, int rowNum) throws SQLException {
    return new CompanySubscription(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        (UUID) rs.getObject("plan_id"),
        rs.getString("status"));
  }
}
