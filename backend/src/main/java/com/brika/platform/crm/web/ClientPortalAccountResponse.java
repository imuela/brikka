package com.brika.platform.crm.web;

import com.brika.platform.crm.ClientPortalAccount;
import java.time.Instant;
import java.util.UUID;

public record ClientPortalAccountResponse(
    UUID id, UUID clientId, String status, Instant lastLoginAt) {

  public static ClientPortalAccountResponse from(ClientPortalAccount account) {
    return new ClientPortalAccountResponse(
        account.id(), account.clientId(), account.status(), account.lastLoginAt());
  }
}
