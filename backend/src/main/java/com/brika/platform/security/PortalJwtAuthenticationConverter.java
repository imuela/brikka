package com.brika.platform.security;

import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.crm.ClientPortalAccountRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps a validated brika-portal token to a client_portal_accounts row by external_identity_id =
 * jwt.sub (ADR-PORTAL-AUTH-001). Never consults UserRepository — a Portal principal cannot resolve
 * to an internal identity by construction, not just by convention. A token with no matching account
 * is rejected as unauthenticated (401), mirroring BrikaJwtAuthenticationConverter.
 */
@Component
public class PortalJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final ClientPortalAccountRepository clientPortalAccountRepository;

  public PortalJwtAuthenticationConverter(
      ClientPortalAccountRepository clientPortalAccountRepository) {
    this.clientPortalAccountRepository = clientPortalAccountRepository;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    ClientPortalAccount account =
        clientPortalAccountRepository
            .findByExternalIdentityId(jwt.getSubject())
            .orElseThrow(() -> new BadCredentialsException("Unknown portal identity"));
    return new PortalAuthenticationToken(jwt, account);
  }
}
