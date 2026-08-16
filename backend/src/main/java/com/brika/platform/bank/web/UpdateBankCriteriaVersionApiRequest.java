package com.brika.platform.bank.web;

import java.time.Instant;
import java.util.Map;

public record UpdateBankCriteriaVersionApiRequest(
    String status, Instant effectiveTo, Map<String, Object> rules) {}
