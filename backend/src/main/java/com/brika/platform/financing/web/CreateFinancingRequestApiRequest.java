package com.brika.platform.financing.web;

import java.math.BigDecimal;

public record CreateFinancingRequestApiRequest(BigDecimal requestedAmount, int termMonths) {}
