package com.brika.platform.security;

import com.brika.platform.crm.ClientPortalAccount;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authentication principal for the Portal Cliente surface (ADR-PORTAL-AUTH-001). Deliberately never
 * wraps a {@code User} — a Portal principal has no relationship to the internal users table.
 */
public class PortalAuthenticationToken extends AbstractAuthenticationToken {

  private final Jwt jwt;
  private final ClientPortalAccount account;

  public PortalAuthenticationToken(Jwt jwt, ClientPortalAccount account) {
    super(List.of(new SimpleGrantedAuthority("ROLE_PORTAL_CLIENT")));
    this.jwt = jwt;
    this.account = account;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return jwt;
  }

  @Override
  public Object getPrincipal() {
    return account;
  }

  public ClientPortalAccount account() {
    return account;
  }
}
