package com.brika.platform.casemgmt;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class CaseClientRepository {

  private static final RowMapper<CaseClient> ROW_MAPPER =
      (rs, rowNum) ->
          new CaseClient(
              (UUID) rs.getObject("case_id"),
              (UUID) rs.getObject("client_id"),
              ParticipationType.valueOf(rs.getString("participation_type")),
              rs.getBoolean("is_primary"));

  private final JdbcTemplate jdbcTemplate;

  public CaseClientRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insert(
      UUID caseId, UUID clientId, ParticipationType participationType, boolean isPrimary) {
    jdbcTemplate.update(
        "INSERT INTO case_clients (case_id, client_id, participation_type, is_primary) VALUES"
            + " (?, ?, ?, ?)",
        caseId,
        clientId,
        participationType.name(),
        isPrimary);
  }

  public List<CaseClient> findAllByCaseId(UUID caseId) {
    return jdbcTemplate.query(
        "SELECT case_id, client_id, participation_type, is_primary FROM case_clients WHERE"
            + " case_id = ?",
        ROW_MAPPER,
        caseId);
  }

  public int countByCaseId(UUID caseId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM case_clients WHERE case_id = ?", Integer.class, caseId);
    return count == null ? 0 : count;
  }

  public void delete(UUID caseId, UUID clientId) {
    jdbcTemplate.update(
        "DELETE FROM case_clients WHERE case_id = ? AND client_id = ?", caseId, clientId);
  }

  public boolean exists(UUID caseId, UUID clientId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM case_clients WHERE case_id = ? AND client_id = ?",
            Integer.class,
            caseId,
            clientId);
    return count != null && count > 0;
  }
}
