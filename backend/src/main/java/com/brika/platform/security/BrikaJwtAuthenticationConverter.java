package com.brika.platform.security;

import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps a validated JWT to a local Brika user by external_identity_id = jwt.sub
 * (19_IDENTITY_OAUTH_SPECIFICATION.md §7): the company_id claim is never trusted from the token,
 * only sub is used, and everything else (role, company_id) is resolved from our own database. A JWT
 * with no matching local user is rejected as unauthenticated (401), not authorized-but-empty.
 */
@Component
public class BrikaJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private final UserRepository userRepository;

  public BrikaJwtAuthenticationConverter(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    User user =
        userRepository
            .findByExternalIdentityId(jwt.getSubject())
            .orElseThrow(() -> new BadCredentialsException("Unknown identity"));
    return new BrikaAuthenticationToken(jwt, user);
  }
}
