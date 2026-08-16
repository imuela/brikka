package com.brika.platform.identity.web;

import com.brika.platform.security.PortalJwtDecoder;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Replaces the real issuer-backed JwtDecoder(s) (SecurityConfig) with ones that trust the bearer
 * token value itself as the subject claim. No Keycloak realm is provisioned for tests (or local dev
 * — ADR-IDENTITY-001 gate review); signature/issuer validation is Spring Security's own well-tested
 * code, not ours, so these tests focus on what we actually wrote: mapping a subject to a local
 * user/portal account and enforcing permission/tenant scope on top of it.
 *
 * <p>Both the internal and the Portal (ADR-PORTAL-AUTH-001) decoder are stubbed identically here —
 * they are different beans of different types (see PortalJwtDecoder's Javadoc for why), so there is
 * no risk of a test accidentally authenticating against the wrong one: each filter chain only ever
 * consults its own decoder/converter pair, exactly as in production.
 */
@TestConfiguration
public class StubJwtDecoderConfig {

  @Bean
  @Primary
  public JwtDecoder testJwtDecoder() {
    return StubJwtDecoderConfig::decode;
  }

  @Bean
  @Primary
  public PortalJwtDecoder testPortalJwtDecoder() {
    return new PortalJwtDecoder(StubJwtDecoderConfig::decode);
  }

  private static Jwt decode(String token) {
    return Jwt.withTokenValue(token)
        .header("alg", "none")
        .subject(token)
        .claim("sub", token)
        .issuedAt(Instant.now().minusSeconds(5))
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }
}
