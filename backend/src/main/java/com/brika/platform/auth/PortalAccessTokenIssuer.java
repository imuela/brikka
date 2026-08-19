package com.brika.platform.auth;

import com.brika.platform.security.SelfIssuedJwtConfig;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Mints short-lived access tokens for Portal Cliente accounts. Deliberately a separate class from
 * {@link UserAccessTokenIssuer} using an independent signing key and issuer — ADR-PORTAL-AUTH-001.
 */
@Component
public class PortalAccessTokenIssuer {

  private final JwtEncoder encoder;
  private final Duration ttl;

  public PortalAccessTokenIssuer(
      @Qualifier("portalSelfIssuedJwtEncoder") JwtEncoder encoder,
      @Value("${brika.security.self-auth.access-token-ttl-seconds:900}") long ttlSeconds) {
    this.encoder = encoder;
    this.ttl = Duration.ofSeconds(ttlSeconds);
  }

  public String issue(String subject) {
    Instant now = Instant.now();
    JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(SelfIssuedJwtConfig.PORTAL_ISSUER)
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }
}
