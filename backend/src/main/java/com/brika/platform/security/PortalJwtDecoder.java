package com.brika.platform.security;

import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Deliberately not a {@code JwtDecoder} itself (composition, not implementation): if this class
 * implemented {@code JwtDecoder}, it would be a candidate for every unqualified {@code JwtDecoder}
 * injection point (including the internal filterChain's), reintroducing exactly the ambiguity this
 * type exists to avoid. {@link SecurityConfig} pulls the wrapped decoder out explicitly with {@link
 * #decoder()} for the Portal-only filter chain.
 */
public final class PortalJwtDecoder {

  private final JwtDecoder decoder;

  public PortalJwtDecoder(JwtDecoder decoder) {
    this.decoder = decoder;
  }

  public JwtDecoder decoder() {
    return decoder;
  }
}
