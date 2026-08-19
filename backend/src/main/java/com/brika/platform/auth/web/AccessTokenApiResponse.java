package com.brika.platform.auth.web;

import com.brika.platform.auth.AccessTokenResult;

public record AccessTokenApiResponse(
    String accessToken, String refreshToken, long expiresInSeconds) {

  public static AccessTokenApiResponse from(AccessTokenResult result) {
    return new AccessTokenApiResponse(
        result.accessToken(), result.refreshToken(), result.expiresInSeconds());
  }
}
