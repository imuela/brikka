package com.brika.platform.identity.web;

import com.brika.platform.identity.Company;
import java.util.UUID;

public record CompanyResponse(
    UUID id, String legalName, String tradeName, String taxId, String status) {

  public static CompanyResponse from(Company company) {
    return new CompanyResponse(
        company.id(), company.legalName(), company.tradeName(), company.taxId(), company.status());
  }
}
