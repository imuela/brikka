package com.brika.platform.bank;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class BankContactRepository {

  private static final String SELECT =
      "SELECT id, company_id, bank_id, owner_user_id, name, position, department, branch, email,"
          + " phone, secondary_phone, notes, visibility, active, created_at, updated_at FROM"
          + " bank_contacts";

  private static final RowMapper<BankContact> ROW_MAPPER =
      (rs, rowNum) ->
          new BankContact(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("bank_id"),
              (UUID) rs.getObject("owner_user_id"),
              rs.getString("name"),
              rs.getString("position"),
              rs.getString("department"),
              rs.getString("branch"),
              rs.getString("email"),
              rs.getString("phone"),
              rs.getString("secondary_phone"),
              rs.getString("notes"),
              rs.getString("visibility"),
              rs.getBoolean("active"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public BankContactRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(
      UUID companyId,
      UUID bankId,
      UUID ownerUserId,
      String name,
      String position,
      String department,
      String branch,
      String email,
      String phone,
      String secondaryPhone,
      String notes,
      String visibility) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO bank_contacts (company_id, bank_id, owner_user_id, name, position,"
            + " department, branch, email, phone, secondary_phone, notes, visibility) VALUES (?,"
            + " ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        bankId,
        ownerUserId,
        name,
        position,
        department,
        branch,
        email,
        phone,
        secondaryPhone,
        notes,
        visibility);
  }

  public Optional<BankContact> findById(UUID id) {
    return jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id).stream().findFirst();
  }

  public List<BankContact> findAllByCompanyId(UUID companyId) {
    return jdbcTemplate.query(
        SELECT + " WHERE company_id = ? ORDER BY name", ROW_MAPPER, companyId);
  }

  public List<BankContact> findAllByCompanyIdAndBankId(UUID companyId, UUID bankId) {
    return jdbcTemplate.query(
        SELECT + " WHERE company_id = ? AND bank_id = ? ORDER BY name",
        ROW_MAPPER,
        companyId,
        bankId);
  }

  public void update(
      UUID id,
      String name,
      String position,
      String department,
      String branch,
      String email,
      String phone,
      String secondaryPhone,
      String notes,
      String visibility,
      boolean active) {
    jdbcTemplate.update(
        "UPDATE bank_contacts SET name = ?, position = ?, department = ?, branch = ?, email = ?,"
            + " phone = ?, secondary_phone = ?, notes = ?, visibility = ?, active = ?, updated_at"
            + " = now() WHERE id = ?",
        name,
        position,
        department,
        branch,
        email,
        phone,
        secondaryPhone,
        notes,
        visibility,
        active,
        id);
  }

  public void delete(UUID id) {
    jdbcTemplate.update("DELETE FROM bank_contacts WHERE id = ?", id);
  }
}
