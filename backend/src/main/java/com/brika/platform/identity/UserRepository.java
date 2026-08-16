package com.brika.platform.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private static final String SELECT_WITH_ROLE =
      "SELECT u.id, u.company_id, u.email, u.first_name, u.last_name, u.status, r.code AS"
          + " role_code FROM users u"
          + " JOIN user_roles ur ON ur.user_id = u.id"
          + " JOIN roles r ON r.id = ur.role_id";

  private static final RowMapper<User> USER_ROW_MAPPER = UserRepository::mapUser;

  private final JdbcTemplate jdbcTemplate;

  public UserRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  UUID insertUser(
      UUID companyId, String externalIdentityId, String email, String firstName, String lastName) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO users (company_id, external_identity_id, email, first_name, last_name,"
            + " status) VALUES (?, ?, ?, ?, ?, 'ACTIVE') RETURNING id",
        UUID.class,
        companyId,
        externalIdentityId,
        email,
        firstName,
        lastName);
  }

  void insertUserRole(UUID userId, UUID roleId) {
    jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
  }

  /** Assumes exactly one role per user (established scope, see ADR-IDENTITY-001 gate review). */
  public Optional<User> findByExternalIdentityId(String externalIdentityId) {
    List<User> users =
        jdbcTemplate.query(
            SELECT_WITH_ROLE + " WHERE u.external_identity_id = ?",
            USER_ROW_MAPPER,
            externalIdentityId);
    return users.stream().findFirst();
  }

  public Optional<User> findById(UUID id) {
    List<User> users =
        jdbcTemplate.query(SELECT_WITH_ROLE + " WHERE u.id = ?", USER_ROW_MAPPER, id);
    return users.stream().findFirst();
  }

  public List<User> findAllByCompanyId(UUID companyId) {
    return jdbcTemplate.query(
        SELECT_WITH_ROLE + " WHERE u.company_id = ? ORDER BY u.email", USER_ROW_MAPPER, companyId);
  }

  public void updateName(UUID id, String firstName, String lastName) {
    jdbcTemplate.update(
        "UPDATE users SET first_name = ?, last_name = ?, updated_at = now() WHERE id = ?",
        firstName,
        lastName,
        id);
  }

  public void disable(UUID id) {
    jdbcTemplate.update(
        "UPDATE users SET status = 'DISABLED', updated_at = now() WHERE id = ?", id);
  }

  private static User mapUser(ResultSet rs, int rowNum) throws SQLException {
    return new User(
        (UUID) rs.getObject("id"),
        (UUID) rs.getObject("company_id"),
        rs.getString("email"),
        rs.getString("first_name"),
        rs.getString("last_name"),
        rs.getString("status"),
        UserRole.valueOf(rs.getString("role_code")));
  }
}
