package com.brika.platform.crm;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Sprint 27, Bloque 3 (FUNCTIONAL_SPECIFICATION.md §6): extended attributes (document, date of
 * birth, nationality, address, employment status) are all nullable — a minimal client still only
 * needs name/email/phone. No uniqueness constraint is placed on the document yet.
 */
public record Client(
    UUID id,
    UUID companyId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String documentType,
    String documentNumber,
    LocalDate dateOfBirth,
    String nationality,
    String address,
    String employmentStatus,
    String status) {}
