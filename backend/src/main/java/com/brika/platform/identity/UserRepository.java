package com.brika.platform.identity;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

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

  UUID findRoleIdByCode(String code) {
    return jdbcTemplate.queryForObject("SELECT id FROM roles WHERE code = ?", UUID.class, code);
  }

  void insertUserRole(UUID userId, UUID roleId) {
    jdbcTemplate.update("INSERT INTO user_roles (user_id, role_id) VALUES (?, ?)", userId, roleId);
  }
}
