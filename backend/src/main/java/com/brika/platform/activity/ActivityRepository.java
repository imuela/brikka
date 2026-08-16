package com.brika.platform.activity;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** ADR-AUDIT-001: functional activity timeline, distinct from audit_events. */
@Repository
public class ActivityRepository {

  private static final RowMapper<Activity> ROW_MAPPER =
      (rs, rowNum) ->
          new Activity(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("actor_user_id"),
              rs.getString("activity_type"),
              rs.getString("summary"),
              rs.getTimestamp("created_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public ActivityRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insert(
      UUID companyId, UUID caseId, UUID actorUserId, String activityType, String summary) {
    jdbcTemplate.update(
        "INSERT INTO activities (company_id, case_id, actor_user_id, activity_type, summary)"
            + " VALUES (?, ?, ?, ?, ?)",
        companyId,
        caseId,
        actorUserId,
        activityType,
        summary);
  }

  public List<Activity> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        "SELECT id, company_id, case_id, actor_user_id, activity_type, summary, created_at FROM"
            + " activities WHERE case_id = ? ORDER BY created_at DESC",
        ROW_MAPPER,
        caseId);
  }

  public List<Activity> findAllByCompanyId(UUID companyId) {
    return jdbcTemplate.query(
        "SELECT id, company_id, case_id, actor_user_id, activity_type, summary, created_at FROM"
            + " activities WHERE company_id = ? ORDER BY created_at DESC",
        ROW_MAPPER,
        companyId);
  }

  /** Dashboard for BROKER: only activities of cases the user is actively assigned to. */
  public List<Activity> findAllAssignedToUser(UUID companyId, UUID userId) {
    return jdbcTemplate.query(
        "SELECT id, company_id, case_id, actor_user_id, activity_type, summary, created_at FROM"
            + " activities WHERE company_id = ? AND case_id IN (SELECT case_id FROM"
            + " case_assignments WHERE user_id = ? AND active = true) ORDER BY created_at DESC",
        ROW_MAPPER,
        companyId,
        userId);
  }
}
