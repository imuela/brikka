package com.brika.platform.financing.web;

import java.math.BigDecimal;

public record UpdateFinancingRequestApiRequest(
    String status, BigDecimal requestedAmount, int termMonths) {}
