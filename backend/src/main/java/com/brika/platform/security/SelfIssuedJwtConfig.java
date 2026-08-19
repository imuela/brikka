package com.brika.platform.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Encoder/decoder beans for Brika's own JWT issuer (autorización Sprint 22 §2). {@link
 * com.brika.platform.auth.UserAccessTokenIssuer} and {@link
 * com.brika.platform.auth.PortalAccessTokenIssuer} use the encoders to mint tokens; {@link
 * SecurityConfig} wires the decoders unconditionally into both {@code SecurityFilterChain}s (Sprint
 * 22 cierre, ADR-AUTH-001 — Keycloak retired, this is now the only issuer).
 */
@Configuration
public class SelfIssuedJwtConfig {

  // http(s)-shaped strings, not resolved over the network anywhere — Jwt#getIssuer() (and some
  // validators) parse the `iss` claim as a URL, so an opaque non-URI string like "brika-internal"
  // breaks that convenience accessor even though signature/expiry validation itself doesn't need
  // it to resolve.
  public static final String INTERNAL_ISSUER = "https://auth.brika.internal/self/internal";
  public static final String PORTAL_ISSUER = "https://auth.brika.internal/self/portal";

  @Bean
  public JwtEncoder internalSelfIssuedJwtEncoder(SelfIssuedTokenKeys keys) {
    return encoder(keys.internalKeyPair(), "brika-internal-1");
  }

  @Bean
  public JwtDecoder internalSelfIssuedJwtDecoder(SelfIssuedTokenKeys keys) {
    return decoder(keys.internalKeyPair(), INTERNAL_ISSUER);
  }

  @Bean
  public JwtEncoder portalSelfIssuedJwtEncoder(SelfIssuedTokenKeys keys) {
    return encoder(keys.portalKeyPair(), "brika-portal-1");
  }

  @Bean
  public JwtDecoder portalSelfIssuedJwtDecoder(SelfIssuedTokenKeys keys) {
    return decoder(keys.portalKeyPair(), PORTAL_ISSUER);
  }

  private static JwtEncoder encoder(java.security.KeyPair keyPair, String keyId) {
    RSAKey rsaKey =
        new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
            .privateKey((RSAPrivateKey) keyPair.getPrivate())
            .keyID(keyId)
            .build();
    return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
  }

  private static JwtDecoder decoder(java.security.KeyPair keyPair, String issuer) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
    return decoder;
  }
}
