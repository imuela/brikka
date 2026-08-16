package com.brika.platform.security;

import java.util.function.Supplier;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Defers the OIDC discovery HTTP call (issuer metadata + JWKS) until the first token is actually
 * decoded, instead of at application context startup. No "brika" Keycloak realm is provisioned yet
 * (Sprint 0 only started bare Keycloak) — building the real decoder eagerly would fail every test
 * and every local boot that doesn't have a reachable issuer, even though nothing ever presents a
 * bearer token in that case.
 */
final class LazyIssuerJwtDecoder implements JwtDecoder {

  private final Supplier<JwtDecoder> delegateSupplier;
  private volatile JwtDecoder delegate;

  LazyIssuerJwtDecoder(Supplier<JwtDecoder> delegateSupplier) {
    this.delegateSupplier = delegateSupplier;
  }

  @Override
  public Jwt decode(String token) throws JwtException {
    return resolveDelegate().decode(token);
  }

  private JwtDecoder resolveDelegate() {
    JwtDecoder result = delegate;
    if (result == null) {
      synchronized (this) {
        result = delegate;
        if (result == null) {
          result = delegateSupplier.get();
          delegate = result;
        }
      }
    }
    return result;
  }
}
