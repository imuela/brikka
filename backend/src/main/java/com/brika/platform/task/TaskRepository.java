package com.brika.platform.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class TaskRepository {

  private static final String SELECT =
      "SELECT id, company_id, case_id, assigned_to, type, title, description, status, due_at,"
          + " created_by, completed_at, created_at, updated_at FROM tasks";

  private static final RowMapper<Task> ROW_MAPPER =
      (rs, rowNum) ->
          new Task(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("assigned_to"),
              rs.getString("type"),
              rs.getString("title"),
              rs.getString("description"),
              rs.getString("status"),
              rs.getTimestamp("due_at") == null ? null : rs.getTimestamp("due_at").toInstant(),
              (UUID) rs.getObject("created_by"),
              rs.getTimestamp("completed_at") == null
                  ? null
                  : rs.getTimestamp("completed_at").toInstant(),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public TaskRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID caseId,
      UUID assignedTo,
      String type,
      String title,
      String description,
      java.time.Instant dueAt,
      UUID createdBy) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO tasks (company_id, case_id, assigned_to, type, title, description, status,"
            + " due_at, created_by) VALUES (?, ?, ?, ?, ?, ?, 'TODO', ?, ?) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        assignedTo,
        type,
        title,
        description,
        dueAt == null ? null : java.sql.Timestamp.from(dueAt),
        createdBy);
  }

  public Optional<Task> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  /** Sprint 27 (ADR-RBAC-002): GLOBAL read for SUPERADMIN across all companies. */
  public List<Task> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY created_at DESC", ROW_MAPPER);
  }

  /**
   * MANAGER/SUPERADMIN see every task in the tenant; BROKER sees caseless tasks plus tasks
   * belonging to cases they are actively assigned to — same CASE ASSIGNMENT rule as everywhere
   * else, applied to a list query instead of a single entity.
   */
  public List<Task> findAllVisibleToUser(
      UUID companyId, boolean restrictToAssignedCases, UUID userId) {
    if (!restrictToAssignedCases) {
      return jdbcTemplate.query(
          SELECT + " WHERE company_id = ? ORDER BY created_at DESC", ROW_MAPPER, companyId);
    }
    return jdbcTemplate.query(
        SELECT
            + " WHERE company_id = ? AND (case_id IS NULL OR EXISTS (SELECT 1 FROM"
            + " case_assignments ca WHERE ca.case_id = tasks.case_id AND ca.user_id = ? AND"
            + " ca.active = true)) ORDER BY created_at DESC",
        ROW_MAPPER,
        companyId,
        userId);
  }

  public void update(
      UUID id,
      String title,
      String description,
      String status,
      java.time.Instant dueAt,
      UUID assignedTo) {
    jdbcTemplate.update(
        "UPDATE tasks SET title = ?, description = ?, status = ?, due_at = ?, assigned_to = ?,"
            + " updated_at = now() WHERE id = ?",
        title,
        description,
        status,
        dueAt == null ? null : java.sql.Timestamp.from(dueAt),
        assignedTo,
        id);
  }

  public void complete(UUID id) {
    jdbcTemplate.update(
        "UPDATE tasks SET status = 'DONE', completed_at = now(), updated_at = now() WHERE id = ?",
        id);
  }

  public void delete(UUID id) {
    jdbcTemplate.update("DELETE FROM tasks WHERE id = ?", id);
  }
}
