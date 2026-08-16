package com.brika.platform.identity.web;

import com.brika.platform.identity.User;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String role,
    UUID companyId,
    String status) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.id(),
        user.email(),
        user.firstName(),
        user.lastName(),
        user.role().name(),
        user.companyId(),
        user.status());
  }
}
