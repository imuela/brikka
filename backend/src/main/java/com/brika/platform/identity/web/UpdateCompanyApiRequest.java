package com.brika.platform.identity.web;

/**
 * Never accepts {@code status}: lifecycle transitions go through the dedicated suspend/delete
 * endpoints (COMPANY_SUSPEND/COMPANY_DELETE), mirroring the Users PATCH/disable split.
 */
public record UpdateCompanyApiRequest(String legalName, String tradeName, String taxId) {}
