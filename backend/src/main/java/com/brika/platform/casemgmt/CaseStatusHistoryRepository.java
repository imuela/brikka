package com.brika.platform.casemgmt;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §10. */
@Repository
public class CaseStatusHistoryRepository {

  private final JdbcTemplate jdbcTemplate;

  public CaseStatusHistoryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insert(
      UUID companyId,
      UUID caseId,
      CaseStatus previousStatus,
      CaseStatus newStatus,
      UUID changedBy,
      String reason) {
    jdbcTemplate.update(
        "INSERT INTO case_status_history (company_id, case_id, previous_status, new_status,"
            + " changed_by, reason) VALUES (?, ?, ?, ?, ?, ?)",
        companyId,
        caseId,
        previousStatus == null ? null : previousStatus.name(),
        newStatus.name(),
        changedBy,
        reason);
  }

  public int countByCaseId(UUID caseId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM case_status_history WHERE case_id = ?", Integer.class, caseId);
    return count == null ? 0 : count;
  }
}
