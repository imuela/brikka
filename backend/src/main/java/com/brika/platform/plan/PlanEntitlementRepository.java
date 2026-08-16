package com.brika.platform.plan;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PlanEntitlementRepository {

  private final JdbcTemplate jdbcTemplate;

  public PlanEntitlementRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void grant(UUID planId, UUID entitlementId, String valueJson) {
    jdbcTemplate.update(
        "INSERT INTO plan_entitlements (plan_id, entitlement_id, value) VALUES (?, ?, ?::jsonb)",
        planId,
        entitlementId,
        valueJson);
  }
}
