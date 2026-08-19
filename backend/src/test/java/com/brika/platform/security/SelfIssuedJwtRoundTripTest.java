package com.brika.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Sprint 22 Fase 1 (autorización, GATE 1): the self-issued encoder/decoder pair actually signs and
 * validates — plain unit test (ephemeral in-process keys, no Spring context, no DB) since this is
 * pure crypto plumbing. SecurityConfig wiring into the live filter chains is Fase 2/3.
 */
class SelfIssuedJwtRoundTripTest {

  private final SelfIssuedTokenKeys keys = new SelfIssuedTokenKeys("", "");
  private final SelfIssuedJwtConfig config = new SelfIssuedJwtConfig();

  @Test
  void internalTokenSignedByInternalEncoderValidatesAgainstInternalDecoder() {
    JwtEncoder encoder = config.internalSelfIssuedJwtEncoder(keys);
    JwtDecoder decoder = config.internalSelfIssuedJwtDecoder(keys);

    String token = issue(encoder, SelfIssuedJwtConfig.INTERNAL_ISSUER, "some-subject");
    Jwt decoded = decoder.decode(token);

    assertThat(decoded.getSubject()).isEqualTo("some-subject");
    assertThat(decoded.getIssuer().toString()).isEqualTo(SelfIssuedJwtConfig.INTERNAL_ISSUER);
  }

  @Test
  void portalTokenSignedByPortalEncoderValidatesAgainstPortalDecoder() {
    JwtEncoder encoder = config.portalSelfIssuedJwtEncoder(keys);
    JwtDecoder decoder = config.portalSelfIssuedJwtDecoder(keys);

    String token = issue(encoder, SelfIssuedJwtConfig.PORTAL_ISSUER, "portal-subject");
    Jwt decoded = decoder.decode(token);

    assertThat(decoded.getSubject()).isEqualTo("portal-subject");
  }

  @Test
  void internalTokenIsRejectedByThePortalDecoder() {
    JwtEncoder internalEncoder = config.internalSelfIssuedJwtEncoder(keys);
    JwtDecoder portalDecoder = config.portalSelfIssuedJwtDecoder(keys);

    String internalToken = issue(internalEncoder, SelfIssuedJwtConfig.INTERNAL_ISSUER, "someone");

    assertThatThrownBy(() -> portalDecoder.decode(internalToken)).isInstanceOf(JwtException.class);
  }

  @Test
  void portalTokenIsRejectedByTheInternalDecoder() {
    JwtEncoder portalEncoder = config.portalSelfIssuedJwtEncoder(keys);
    JwtDecoder internalDecoder = config.internalSelfIssuedJwtDecoder(keys);

    String portalToken = issue(portalEncoder, SelfIssuedJwtConfig.PORTAL_ISSUER, "someone");

    assertThatThrownBy(() -> internalDecoder.decode(portalToken)).isInstanceOf(JwtException.class);
  }

  @Test
  void expiredTokenIsRejected() {
    JwtEncoder encoder = config.internalSelfIssuedJwtEncoder(keys);
    JwtDecoder decoder = config.internalSelfIssuedJwtDecoder(keys);
    Instant past = Instant.now().minusSeconds(3600);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(SelfIssuedJwtConfig.INTERNAL_ISSUER)
            .subject("someone")
            .issuedAt(past)
            .expiresAt(past.plusSeconds(60))
            .build();
    String token =
        encoder
            .encode(
                JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
            .getTokenValue();

    assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void tamperedTokenSignatureIsRejected() {
    JwtEncoder encoder = config.internalSelfIssuedJwtEncoder(keys);
    JwtDecoder decoder = config.internalSelfIssuedJwtDecoder(keys);
    String token = issue(encoder, SelfIssuedJwtConfig.INTERNAL_ISSUER, "someone");
    int lastDot = token.lastIndexOf('.');
    String signature = token.substring(lastDot + 1);
    char flipped = signature.charAt(0) == 'A' ? 'B' : 'A';
    String tampered = token.substring(0, lastDot + 1) + flipped + signature.substring(1);

    assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(JwtException.class);
  }

  private static String issue(JwtEncoder encoder, String issuer, String subject) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(900))
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
        .getTokenValue();
  }
}
