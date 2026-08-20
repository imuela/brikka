package com.brika.platform.crm.web;

import java.time.LocalDate;

/** Sprint 27, Bloque 3: mirrors CreateClientApiRequest — all fields optional, partial updates. */
public record UpdateClientApiRequest(
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
