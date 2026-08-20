package com.brika.platform.crm.web;

import java.time.LocalDate;

/** Sprint 27, Bloque 3: extended attributes are optional; only name/email/phone are required. */
public record CreateClientApiRequest(
    String firstName,
    String lastName,
    String email,
    String phone,
    String documentType,
    String documentNumber,
    LocalDate dateOfBirth,
    String nationality,
    String address,
    String employmentStatus) {}
