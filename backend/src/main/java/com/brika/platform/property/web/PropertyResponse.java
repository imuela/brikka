package com.brika.platform.property.web;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record PropertyResponse(
    UUID id,
    UUID companyId,
    UUID caseId,
    Map<String, Object> address,
    String propertyType,
    BigDecimal valuation,
    BigDecimal purchasePrice) {}
