package com.brika.platform.bankmatching.web;

public record CreateBankMatchRuleOverrideApiRequest(
    String previousResult, String newResult, String reason) {}
