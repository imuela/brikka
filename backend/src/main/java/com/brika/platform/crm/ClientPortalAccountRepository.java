package com.brika.platform.crm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * external_identity_id resolves the sub claim of tokens issued by the brika-portal realm
 * (ADR-PORTAL-AUTH-001) — never the internal realm, never users.external_identity_id.
 */
@Repository
public class ClientPortalAccountRepository {

  private static final String SELECT =
      "SELECT id, company_id, client_id, external_identity_id, status, last_login_at FROM"
          + " client_portal_accounts";

  private static final RowMapper<ClientPortalAccount> ROW_MAPPER =
      (rs, rowNum) ->
          new ClientPortalAccount(
              (UUID) rs.getObject("id"),
              (UUID) rs.getObject("company_id"),
              (UUID) rs.getObject("client_id"),
              rs.getString("external_identity_id"),
              rs.getString("status"),
              rs.getTimestamp("last_login_at") == null
                  ? null
                  : rs.getTimestamp("last_login_at").toInstant());

  private final JdbcTemplate jdbcTemplate;

  public ClientPortalAccountRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public UUID insert(UUID companyId, UUID clientId, String externalIdentityId, String status) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO client_portal_accounts (company_id, client_id, external_identity_id, status)"
            + " VALUES (?, ?, ?, ?) RETURNING id",
        UUID.class,
        companyId,
        clientId,
        externalIdentityId,
        status);
  }

  public Optional<ClientPortalAccount> findById(UUID id) {
    List<ClientPortalAccount> accounts =
        jdbcTemplate.query(SELECT + " WHERE id = ?", ROW_MAPPER, id);
    return accounts.stream().findFirst();
  }

  public Optional<ClientPortalAccount> findByClientId(UUID clientId) {
    List<ClientPortalAccount> accounts =
        jdbcTemplate.query(SELECT + " WHERE client_id = ?", ROW_MAPPER, clientId);
    return accounts.stream().findFirst();
  }

  public Optional<ClientPortalAccount> findByExternalIdentityId(String externalIdentityId) {
    List<ClientPortalAccount> accounts =
        jdbcTemplate.query(
            SELECT + " WHERE external_identity_id = ?", ROW_MAPPER, externalIdentityId);
    return accounts.stream().findFirst();
  }

  public void updateLastLoginAt(UUID id) {
    jdbcTemplate.update(
        "UPDATE client_portal_accounts SET last_login_at = now(), updated_at = now() WHERE id ="
            + " ?",
        id);
  }
}
