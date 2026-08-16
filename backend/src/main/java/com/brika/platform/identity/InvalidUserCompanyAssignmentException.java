package com.brika.platform.identity;

/** Thrown when a user's company_id does not match what ADR-IDENTITY-001 requires for its role. */
public class InvalidUserCompanyAssignmentException extends RuntimeException {

  public InvalidUserCompanyAssignmentException(String message) {
    super(message);
  }
}
