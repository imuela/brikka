package com.brika.platform.security;

import com.brika.platform.identity.User;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Authentication principal after a valid JWT is mapped to a local Brika user (ADR-IDENTITY-001).
 */
public class BrikaAuthenticationToken extends AbstractAuthenticationToken {

  private final Jwt jwt;
  private final User user;

  public BrikaAuthenticationToken(Jwt jwt, User user) {
    super(List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
    this.jwt = jwt;
    this.user = user;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return jwt;
  }

  @Override
  public Object getPrincipal() {
    return user;
  }

  public User user() {
    return user;
  }
}
