package com.brika.platform.bankrequest.web;

import java.math.BigDecimal;
import java.util.Map;

public record CreateBankOfferApiRequest(
    BigDecimal amount,
    BigDecimal interestRate,
    int termMonths,
    BigDecimal payment,
    Map<String, Object> conditions) {}
