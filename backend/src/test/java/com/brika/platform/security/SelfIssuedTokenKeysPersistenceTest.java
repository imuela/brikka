package com.brika.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
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
 * Sprint 24 (claves JWT persistentes): demuestra que un token firmado con una clave base64 PKCS8
 * sigue validándose en una {@link SelfIssuedTokenKeys} nueva construida con LA MISMA clave — es
 * decir, que los tokens sobreviven a un reinicio del backend cuando las claves se persisten (lo que
 * antes solo era posible con el par efímero por proceso). Además confirma que una clave distinta
 * invalida el token (separación Internal/Portal y detección de cambio de clave).
 */
class SelfIssuedTokenKeysPersistenceTest {

  private static final String INTERNAL = "https://auth.brika.internal/self/internal";
  private static final String PORTAL = "https://auth.brika.internal/self/portal";

  @Test
  void tokenSurvivesARestartWithTheSamePersistedKey() throws Exception {
    String internalKey = generateBase64Pkcs8();
    String portalKey = generateBase64Pkcs8();

    // "Primer arranque": emite un token con estas claves.
    SelfIssuedTokenKeys firstBoot = new SelfIssuedTokenKeys(internalKey, portalKey);
    SelfIssuedJwtConfig config = new SelfIssuedJwtConfig();
    String token = issue(config.internalSelfIssuedJwtEncoder(firstBoot), INTERNAL, "subject-1");

    // "Reinicio": nueva instancia con el MISMO material de clave persistido.
    SelfIssuedTokenKeys secondBoot = new SelfIssuedTokenKeys(internalKey, portalKey);
    JwtDecoder afterRestart = config.internalSelfIssuedJwtDecoder(secondBoot);

    Jwt decoded = afterRestart.decode(token);
    assertThat(decoded.getSubject()).isEqualTo("subject-1");
  }

  @Test
  void aDifferentKeyInvalidatesPreviouslyIssuedTokens() throws Exception {
    String internalKey = generateBase64Pkcs8();
    String portalKey = generateBase64Pkcs8();

    SelfIssuedTokenKeys original = new SelfIssuedTokenKeys(internalKey, portalKey);
    SelfIssuedJwtConfig config = new SelfIssuedJwtConfig();
    String token = issue(config.internalSelfIssuedJwtEncoder(original), INTERNAL, "subject-2");

    // Clave rotada/distinta: el mismo token ya no debe validar.
    SelfIssuedTokenKeys rotated = new SelfIssuedTokenKeys(generateBase64Pkcs8(), portalKey);
    JwtDecoder withNewKey = config.internalSelfIssuedJwtDecoder(rotated);

    assertThatThrownBy(() -> withNewKey.decode(token)).isInstanceOf(JwtException.class);
  }

  @Test
  void persistedPortalKeySurvivesRestartIndependently() throws Exception {
    String internalKey = generateBase64Pkcs8();
    String portalKey = generateBase64Pkcs8();

    SelfIssuedTokenKeys firstBoot = new SelfIssuedTokenKeys(internalKey, portalKey);
    SelfIssuedJwtConfig config = new SelfIssuedJwtConfig();
    String token = issue(config.portalSelfIssuedJwtEncoder(firstBoot), PORTAL, "portal-1");

    SelfIssuedTokenKeys secondBoot = new SelfIssuedTokenKeys(internalKey, portalKey);
    Jwt decoded = config.portalSelfIssuedJwtDecoder(secondBoot).decode(token);
    assertThat(decoded.getSubject()).isEqualTo("portal-1");
  }

  private static String generateBase64Pkcs8() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair pair = generator.generateKeyPair();
    return Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
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
