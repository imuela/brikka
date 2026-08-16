package com.brika.platform.identity.web;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Replaces the real issuer-backed JwtDecoder (SecurityConfig) with one that trusts the bearer token
 * value itself as the subject claim. No Keycloak realm is provisioned for tests (or local dev —
 * ADR-IDENTITY-001 gate review); signature/issuer validation is Spring Security's own well-tested
 * code, not ours, so these tests focus on what we actually wrote: mapping a subject to a local user
 * and enforcing permission/tenant scope on top of it.
 */
@TestConfiguration
public class StubJwtDecoderConfig {

  @Bean
  @Primary
  public JwtDecoder testJwtDecoder() {
    return token ->
        Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject(token)
            .claim("sub", token)
            .issuedAt(Instant.now().minusSeconds(5))
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
  }
}
