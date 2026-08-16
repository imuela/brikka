package com.brika.platform.casemgmt.web;

import java.util.UUID;

/** participationType must be one of: HOLDER, CO_HOLDER, GUARANTOR, OTHER. */
public record CaseClientApiRequest(UUID clientId, String participationType, boolean isPrimary) {}
