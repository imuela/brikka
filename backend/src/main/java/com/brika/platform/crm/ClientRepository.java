package com.brika.platform.crm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClientRepository {

  private static final String SELECT =
      "SELECT id, company_id, first_name, last_name, email, phone, status FROM clients";

  private static final RowMapper<Client> ROW_MAPPER =
      (rs, rowNum) ->
          new Client(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              rs.getString("first_name"),
              rs.getString("last_name"),
              rs.getString("email"),
              rs.getString("phone"),
              rs.getString("status"));

  private final JdbcTemplate jdbcTemplate;

  public ClientRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** status defaults to ACTIVE — no enum is documented anywhere (same category as users.status). */
  public UUID insert(
      UUID companyId, String firstName, String lastName, String email, String phone) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO clients (company_id, first_name, last_name, email, phone, status) VALUES"
            + " (?, ?, ?, ?, ?, 'ACTIVE') RETURNING id",
        UUID.class,
        companyId,
        firstName,
        lastName,
        email,
        phone);
  }

  public Optional<Client> findById(UUID id) {
    List<Client> clients = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return clients.stream().findFirst();
  }

  public List<Client> findAllByCompanyId(UUID companyId) {
    return jdbcTemplate.query(
        SELECT + " WHERE company_id = ? ORDER BY last_name, first_name", ROW_MAPPER, companyId);
  }

  public void update(UUID id, String firstName, String lastName, String email, String phone) {
    jdbcTemplate.update(
        "UPDATE clients SET first_name = ?, last_name = ?, email = ?, phone = ?, updated_at ="
            + " now() WHERE id = ?",
        firstName,
        lastName,
        email,
        phone,
        id);
  }

  /**
   * Portal Cliente (PATCH /portal/profile, approved editable fields): never touches first_name/
   * last_name — those require broker/manager action via {@link #update}.
   */
  public void updateContactInfo(UUID id, String email, String phone) {
    jdbcTemplate.update(
        "UPDATE clients SET email = ?, phone = ?, updated_at = now() WHERE id = ?",
        email,
        phone,
        id);
  }
}
