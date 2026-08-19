package com.brika.platform.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * SHA-256 hashing for high-entropy opaque tokens (refresh tokens, password reset tokens). Unlike a
 * password, an opaque token is already cryptographically random, so a fast, indexable hash is
 * correct here — Argon2id's deliberate computational cost defends against small guessable password
 * spaces, which does not apply to a 256-bit random value.
 */
public final class TokenHasher {

  private TokenHasher() {}

  public static String sha256Hex(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 must be available", e);
    }
  }
}
