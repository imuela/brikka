package com.brika.platform.auth;

/**
 * Generic authentication failure — unknown identifier, wrong password, ambiguous email match
 * (ADR-IDENTITY-001), or disabled account all raise this same exception with the same message,
 * deliberately: the caller must never be able to distinguish "no such account" from "wrong
 * password" (autorización Sprint 22 §11, "no revelar si el usuario existe").
 */
public class AuthenticationFailedException extends RuntimeException {

  public AuthenticationFailedException() {
    super("Invalid credentials");
  }
}
