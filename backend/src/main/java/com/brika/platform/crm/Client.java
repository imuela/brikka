package com.brika.platform.crm;

import java.util.UUID;

public record Client(
    UUID id,
    UUID companyId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String status) {}
