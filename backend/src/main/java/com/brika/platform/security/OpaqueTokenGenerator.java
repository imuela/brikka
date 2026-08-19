package com.brika.platform.security;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates high-entropy opaque values for refresh tokens and password reset tokens. */
public final class OpaqueTokenGenerator {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  private OpaqueTokenGenerator() {}

  public static String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
