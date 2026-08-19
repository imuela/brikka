package com.brika.platform.auth;

/** Plain data carrier returned to the login/refresh controllers — no identity resolution. */
public record AccessTokenResult(String accessToken, String refreshToken, long expiresInSeconds) {}
