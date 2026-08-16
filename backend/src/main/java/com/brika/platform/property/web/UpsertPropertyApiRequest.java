package com.brika.platform.property.web;

import java.math.BigDecimal;
import java.util.Map;

public record UpsertPropertyApiRequest(
    Map<String, Object> address,
    String propertyType,
    BigDecimal valuation,
    BigDecimal purchasePrice) {}
