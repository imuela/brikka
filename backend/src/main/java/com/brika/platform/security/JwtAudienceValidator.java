package com.brika.platform.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 19_IDENTITY_OAUTH_SPECIFICATION.md §4 requires validating audience; only active when configured.
 */
final class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final String expectedAudience;

  JwtAudienceValidator(String expectedAudience) {
    this.expectedAudience = expectedAudience;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    if (token.getAudience() != null && token.getAudience().contains(expectedAudience)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error("invalid_token", "Required audience is missing", null));
  }
}
