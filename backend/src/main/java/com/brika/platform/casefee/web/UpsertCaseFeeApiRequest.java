package com.brika.platform.casefee.web;

import java.math.BigDecimal;
import java.time.Instant;

public record UpsertCaseFeeApiRequest(
    String feeType,
    BigDecimal fixedAmount,
    BigDecimal percentage,
    BigDecimal calculationBase,
    String status,
    Instant agreedAt) {}
