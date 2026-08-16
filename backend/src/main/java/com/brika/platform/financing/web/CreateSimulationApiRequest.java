package com.brika.platform.financing.web;

import java.math.BigDecimal;
import java.util.Map;

public record CreateSimulationApiRequest(
    BigDecimal principal,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal estimatedPayment,
    Map<String, Object> metadata) {}
