package com.brika.platform.auth;

/** Login lockout (autorización §11). Does not reveal whether the identifier itself exists. */
public class TooManyLoginAttemptsException extends RuntimeException {

  public TooManyLoginAttemptsException() {
    super("Too many login attempts");
  }
}
