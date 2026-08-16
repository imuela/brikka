package com.brika.platform.identity;

import java.util.UUID;

public record CreateUserCommand(
    UserRole role,
    UUID companyId,
    String externalIdentityId,
    String email,
    String firstName,
    String lastName) {}
