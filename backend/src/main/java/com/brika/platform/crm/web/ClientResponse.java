package com.brika.platform.crm.web;

import com.brika.platform.crm.Client;
import java.time.LocalDate;
import java.util.UUID;

public record ClientResponse(
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
    String status) {

  public static ClientResponse from(Client client) {
    return new ClientResponse(
        client.id(),
        client.companyId(),
        client.firstName(),
        client.lastName(),
        client.email(),
        client.phone(),
        client.documentType(),
        client.documentNumber(),
        client.dateOfBirth(),
        client.nationality(),
        client.address(),
        client.employmentStatus(),
        client.status());
  }
}
