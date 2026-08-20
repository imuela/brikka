package com.brika.platform.dashboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-model behind GET /api/v1/dashboard (Sprint 27, Bloque 2 — FUNCTIONAL_SPECIFICATION.md §3).
 * All metrics are aggregate queries over the operational tables. The scope is resolved by the
 * controller: tenantId is null for a GLOBAL SUPERADMIN, and restrictToAssignedCases applies the
 * same CASE ASSIGNMENT rule as every list endpoint for a BROKER.
 */
@Repository
public class DashboardRepository {

  private final JdbcTemplate jdbcTemplate;

  public DashboardRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** tenantId null => global (SUPERADMIN); restrictToAssignedCases only ever true for BROKER. */
  public record DashboardScope(UUID tenantId, UUID userId, boolean restrictToAssignedCases) {}

  public int countActiveCases(DashboardScope scope) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT count(*) FROM cases WHERE status NOT IN ('COMPLETED','CANCELLED')");
    List<Object> args = new ArrayList<>();
    appendCompany(scope, sql, args, "company_id");
    appendAssignedCaseFilter(scope, sql, args);
    return queryCount(sql.toString(), args);
  }

  public Map<String, Integer> countCasesByStatus(DashboardScope scope) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT status, count(*) FROM cases WHERE status NOT IN ('COMPLETED','CANCELLED')");
    List<Object> args = new ArrayList<>();
    appendCompany(scope, sql, args, "company_id");
    appendAssignedCaseFilter(scope, sql, args);
    sql.append(" GROUP BY status");
    return jdbcTemplate.query(
        sql.toString(),
        rs -> {
          Map<String, Integer> counts = new LinkedHashMap<>();
          while (rs.next()) {
            counts.put(rs.getString("status"), rs.getInt("count"));
          }
          return counts;
        },
        args.toArray());
  }

  public int countPendingTasks(DashboardScope scope) {
    StringBuilder sql = new StringBuilder("SELECT count(*) FROM tasks WHERE status <> 'DONE'");
    List<Object> args = new ArrayList<>();
    appendCompany(scope, sql, args, "company_id");
    appendBrokerTaskFilter(scope, sql, args);
    return queryCount(sql.toString(), args);
  }

  public int countOverdueTasks(DashboardScope scope) {
    StringBuilder sql =
        new StringBuilder("SELECT count(*) FROM tasks WHERE status <> 'DONE' AND due_at < now()");
    List<Object> args = new ArrayList<>();
    appendCompany(scope, sql, args, "company_id");
    appendBrokerTaskFilter(scope, sql, args);
    return queryCount(sql.toString(), args);
  }

  public int countPendingDocumentRequests(DashboardScope scope) {
    StringBuilder sql =
        new StringBuilder("SELECT count(*) FROM document_requests WHERE status = 'PENDING'");
    List<Object> args = new ArrayList<>();
    appendCompany(scope, sql, args, "company_id");
    if (scope.restrictToAssignedCases()) {
      sql.append(
          " AND EXISTS (SELECT 1 FROM case_assignments ca WHERE ca.case_id ="
              + " document_requests.case_id AND ca.user_id = ? AND ca.active = true)");
      args.add(scope.userId());
    }
    return queryCount(sql.toString(), args);
  }

  private void appendCompany(
      DashboardScope scope, StringBuilder sql, List<Object> args, String column) {
    if (scope.tenantId() != null) {
      sql.append(" AND ").append(column).append(" = ?");
      args.add(scope.tenantId());
    }
  }

  private void appendAssignedCaseFilter(
      DashboardScope scope, StringBuilder sql, List<Object> args) {
    if (scope.restrictToAssignedCases()) {
      sql.append(
          " AND id IN (SELECT case_id FROM case_assignments WHERE user_id = ? AND active = true)");
      args.add(scope.userId());
    }
  }

  private void appendBrokerTaskFilter(DashboardScope scope, StringBuilder sql, List<Object> args) {
    if (scope.restrictToAssignedCases()) {
      sql.append(
          " AND (case_id IS NULL OR EXISTS (SELECT 1 FROM case_assignments ca WHERE ca.case_id ="
              + " tasks.case_id AND ca.user_id = ? AND ca.active = true))");
      args.add(scope.userId());
    }
  }

  private int queryCount(String sql, List<Object> args) {
    Integer n = jdbcTemplate.queryForObject(sql, Integer.class, args.toArray());
    return n == null ? 0 : n;
  }
}
