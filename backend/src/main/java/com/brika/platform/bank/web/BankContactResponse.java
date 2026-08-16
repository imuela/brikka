package com.brika.platform.bank.web;

import com.brika.platform.bank.BankContact;
import java.time.Instant;
import java.util.UUID;

public record BankContactResponse(
    UUID id,
    UUID bankId,
    UUID ownerUserId,
    String name,
    String position,
    String department,
    String branch,
    String email,
    String phone,
    String secondaryPhone,
    String notes,
    String visibility,
    boolean active,
    Instant createdAt,
    Instant updatedAt) {

  public static BankContactResponse from(BankContact contact) {
    return new BankContactResponse(
        contact.id(),
        contact.bankId(),
        contact.ownerUserId(),
        contact.name(),
        contact.position(),
        contact.department(),
        contact.branch(),
        contact.email(),
        contact.phone(),
        contact.secondaryPhone(),
        contact.notes(),
        contact.visibility(),
        contact.active(),
        contact.createdAt(),
        contact.updatedAt());
  }
}
