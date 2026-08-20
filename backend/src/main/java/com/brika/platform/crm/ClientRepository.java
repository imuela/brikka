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
      "SELECT id, company_id, first_name, last_name, email, phone, document_type,"
          + " document_number, date_of_birth, nationality, address, employment_status, status"
          + " FROM clients";

  private static final RowMapper<Client> ROW_MAPPER =
      (rs, rowNum) ->
          new Client(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              rs.getString("first_name"),
              rs.getString("last_name"),
              rs.getString("email"),
              rs.getString("phone"),
              rs.getString("document_type"),
              rs.getString("document_number"),
              rs.getObject("date_of_birth", java.time.LocalDate.class),
              rs.getString("nationality"),
              rs.getString("address"),
              rs.getString("employment_status"),
              rs.getString("status"));

  private final JdbcTemplate jdbcTemplate;

  public ClientRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** status defaults to ACTIVE — no enum is documented anywhere (same category as users.status). */
  /** Convenience overload (legacy callers/tests) with the extended attributes empty. */
  public UUID insert(
      UUID companyId, String firstName, String lastName, String email, String phone) {
    return insert(companyId, firstName, lastName, email, phone, null, null, null, null, null, null);
  }

  public UUID insert(
      UUID companyId,
      String firstName,
      String lastName,
      String email,
      String phone,
      String documentType,
      String documentNumber,
      java.time.LocalDate dateOfBirth,
      String nationality,
      String address,
      String employmentStatus) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO clients (company_id, first_name, last_name, email, phone, status,"
            + " document_type, document_number, date_of_birth, nationality, address,"
            + " employment_status) VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        firstName,
        lastName,
        email,
        phone,
        documentType,
        documentNumber,
        dateOfBirth == null ? null : java.sql.Date.valueOf(dateOfBirth),
        nationality,
        address,
        employmentStatus);
  }

  public Optional<Client> findById(UUID id) {
    List<Client> clients = jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return clients.stream().findFirst();
  }

  /**
   * clients.email has no uniqueness constraint at all (not even per-company) — same "more than one
   * match is a generic auth failure, never silently picked" policy as UserRepository#findAllByEmail
   * (Sprint 22 authorization decision).
   */
  public List<Client> findAllByEmail(String email) {
    return jdbcTemplate.query(SELECT + " WHERE email = ?", ROW_MAPPER, email);
  }

  public List<Client> findAllByCompanyId(UUID companyId) {
    return jdbcTemplate.query(
        SELECT + " WHERE company_id = ? ORDER BY last_name, first_name", ROW_MAPPER, companyId);
  }

  /** Sprint 27 (ADR-RBAC-002): GLOBAL read for SUPERADMIN across all companies. */
  public List<Client> findAll() {
    return jdbcTemplate.query(SELECT + " ORDER BY last_name, first_name", ROW_MAPPER);
  }

  public void update(
      UUID id,
      String firstName,
      String lastName,
      String email,
      String phone,
      String documentType,
      String documentNumber,
      java.time.LocalDate dateOfBirth,
      String nationality,
      String address,
      String employmentStatus) {
    jdbcTemplate.update(
        "UPDATE clients SET first_name = ?, last_name = ?, email = ?, phone = ?,"
            + " document_type = ?, document_number = ?, date_of_birth = ?, nationality = ?,"
            + " address = ?, employment_status = ?, updated_at = now() WHERE id = ?",
        firstName,
        lastName,
        email,
        phone,
        documentType,
        documentNumber,
        dateOfBirth == null ? null : java.sql.Date.valueOf(dateOfBirth),
        nationality,
        address,
        employmentStatus,
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
