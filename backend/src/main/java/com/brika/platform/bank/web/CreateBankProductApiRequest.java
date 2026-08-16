package com.brika.platform.bank.web;

import java.util.Map;

public record CreateBankProductApiRequest(String code, String name, Map<String, Object> metadata) {}
