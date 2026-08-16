package com.brika.platform.casemgmt;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CaseAssignmentRepository {

  private static final RowMapper<CaseAssignment> ROW_MAPPER =
      (rs, rowNum) ->
          new CaseAssignment(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("user_id"),
              rs.getString("assignment_type"),
              rs.getBoolean("active"));

  private final JdbcTemplate jdbcTemplate;

  public CaseAssignmentRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID companyId, UUID caseId, UUID userId, String assignmentType) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO case_assignments (company_id, case_id, user_id, assignment_type, active)"
            + " VALUES (?, ?, ?, ?, true) RETURNING id",
        UUID.class,
        companyId,
        caseId,
        userId,
        assignmentType);
  }

  public List<CaseAssignment> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        "SELECT id, company_id, case_id, user_id, assignment_type, active FROM case_assignments"
            + " WHERE case_id = ? ORDER BY created_at",
        ROW_MAPPER,
        caseId);
  }

  public boolean hasActiveAssignment(UUID caseId, UUID userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM case_assignments WHERE case_id = ? AND user_id = ? AND active ="
                + " true",
            Integer.class,
            caseId,
            userId);
    return count != null && count > 0;
  }
}
