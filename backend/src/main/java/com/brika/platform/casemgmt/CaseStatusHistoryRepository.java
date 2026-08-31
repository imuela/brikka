package com.brika.platform.casemgmt;

import java.util.List;
import java.util.Optional;
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

  /**
   * BRIKKA V2 I3: the reason of the most recent status change — used to verify the
   * "[PRECONDITION_OVERRIDE] " marker persisted when a transition precondition was overridden.
   * {@code reason} is nullable; an empty result means "no history yet".
   */
  public Optional<String> findLatestReasonByCaseId(UUID caseId) {
    List<String> reasons =
        jdbcTemplate.queryForList(
            "SELECT reason FROM case_status_history WHERE case_id = ? ORDER BY changed_at DESC"
                + " LIMIT 1",
            String.class,
            caseId);
    return reasons.stream().findFirst();
  }
}
