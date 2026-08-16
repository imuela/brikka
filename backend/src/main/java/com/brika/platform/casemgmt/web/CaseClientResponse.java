package com.brika.platform.casemgmt.web;

import java.util.UUID;

public record CaseClientResponse(
    UUID clientId,
    String firstName,
    String lastName,
    String participationType,
    boolean isPrimary) {}
