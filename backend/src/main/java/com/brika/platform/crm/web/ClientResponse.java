package com.brika.platform.crm.web;

import com.brika.platform.crm.Client;
import java.util.UUID;

public record ClientResponse(
    UUID id,
    UUID companyId,
    String firstName,
    String lastName,
    String email,
    String phone,
    String status) {

  public static ClientResponse from(Client client) {
    return new ClientResponse(
        client.id(),
        client.companyId(),
        client.firstName(),
        client.lastName(),
        client.email(),
        client.phone(),
        client.status());
  }
}
