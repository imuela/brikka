package com.brika.platform.bank.web;

import java.time.Instant;
import java.util.Map;

/** rules is stored/versioned only in Sprint 5 — not interpreted or executed (Sprint 6 scope). */
public record CreateBankCriteriaVersionApiRequest(
    String version, Instant effectiveFrom, Instant effectiveTo, Map<String, Object> rules) {}
