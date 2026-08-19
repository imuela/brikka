package com.brika.platform.security;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RSA keypairs for Brika's own JWT issuer (autorización Sprint 22 §2/§16). Internal and Portal use
 * cryptographically independent keys — a token signed with one can never validate against the
 * other's decoder, mirroring the separation ADR-PORTAL-AUTH-001 already gets from two distinct
 * Keycloak realms today.
 *
 * <p>Keys are supplied as base64-encoded PKCS8 DER (not full PEM — no PEM-parsing dependency
 * needed) via {@code brika.security.self-auth.*-signing-key-pem}. If unset, an ephemeral keypair is
 * generated for this process only: correct for local dev (never introduces infrastructure, §16),
 * but tokens stop validating across restarts — a real deployment must set both keys as secrets,
 * never committed to source control (§16).
 */
@Component
public class SelfIssuedTokenKeys {

  private static final Logger log = LoggerFactory.getLogger(SelfIssuedTokenKeys.class);

  private final KeyPair internalKeyPair;
  private final KeyPair portalKeyPair;

  public SelfIssuedTokenKeys(
      @Value("${brika.security.self-auth.internal-signing-key-pem:}") String internalKeyPem,
      @Value("${brika.security.self-auth.portal-signing-key-pem:}") String portalKeyPem) {
    this.internalKeyPair = loadOrGenerate(internalKeyPem, "internal");
    this.portalKeyPair = loadOrGenerate(portalKeyPem, "portal");
  }

  public KeyPair internalKeyPair() {
    return internalKeyPair;
  }

  public KeyPair portalKeyPair() {
    return portalKeyPair;
  }

  private static KeyPair loadOrGenerate(String base64Pkcs8PrivateKey, String label) {
    if (StringUtils.hasText(base64Pkcs8PrivateKey)) {
      return decode(base64Pkcs8PrivateKey, label);
    }
    log.warn(
        "No persistent {} self-auth signing key configured"
            + " (brika.security.self-auth.{}-signing-key-pem) — generating an ephemeral RSA key"
            + " for this process only. Tokens will stop validating across restarts. Do not run"
            + " like this in production.",
        label,
        label);
    return generate();
  }

  private static KeyPair decode(String base64Pkcs8PrivateKey, String label) {
    try {
      byte[] der = Base64.getDecoder().decode(base64Pkcs8PrivateKey);
      KeyFactory keyFactory = KeyFactory.getInstance("RSA");
      RSAPrivateCrtKey privateKey =
          (RSAPrivateCrtKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
      PublicKey publicKey =
          keyFactory.generatePublic(
              new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
      return new KeyPair(publicKey, privateKey);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Invalid " + label + " self-auth signing key configuration", e);
    }
  }

  private static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("RSA must be available", e);
    }
  }
}
