package com.brika.platform.plan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Pure query (company_subscriptions -> plan_entitlements -> entitlements), per ADR-PLATFORM-001.
 * Returns the entitlement codes and raw JSON values granted by a company's current subscription
 * plan; does not evaluate subscription status, does not combine with RBAC permission, and is not
 * wired to any endpoint. Functional "permission + entitlement" authorization (06_SECURITY_
 * SPECIFICATION.md §3.1) requires both this and PermissionResolutionService, plus real
 * authentication — out of scope for this block.
 */
@Service
public class EntitlementResolutionService {

  private final JdbcTemplate jdbcTemplate;

  public EntitlementResolutionService(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Map<String, String> entitlementValuesForCompany(UUID companyId) {
    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT e.code AS code, pe.value AS value FROM company_subscriptions cs"
                + " JOIN plan_entitlements pe ON pe.plan_id = cs.plan_id"
                + " JOIN entitlements e ON e.id = pe.entitlement_id"
                + " WHERE cs.company_id = ?",
            companyId);

    Map<String, String> result = new HashMap<>();
    for (Map<String, Object> row : rows) {
      result.put((String) row.get("code"), String.valueOf(row.get("value")));
    }
    return result;
  }
}
