package com.brika.platform.property;

import java.math.BigDecimal;
import java.util.UUID;

/** address is raw JSON text (properties.address is jsonb, e.g. {"street":"...","city":"..."}). */
public record Property(
    UUID id,
    UUID companyId,
    UUID caseId,
    String address,
    String propertyType,
    BigDecimal valuation,
    BigDecimal purchasePrice) {}
