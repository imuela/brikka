package com.brika.platform.portal.web;

import com.brika.platform.casemgmt.Case;
import java.time.Instant;
import java.util.UUID;

public record PortalCaseResponse(
    UUID id, String reference, String status, String operationType, Instant createdAt) {

  public static PortalCaseResponse from(Case theCase) {
    return new PortalCaseResponse(
        theCase.id(),
        theCase.reference(),
        theCase.status().name(),
        theCase.operationType(),
        theCase.createdAt());
  }
}
