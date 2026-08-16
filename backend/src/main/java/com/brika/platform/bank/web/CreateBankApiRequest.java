package com.brika.platform.bank.web;

import java.util.Map;

public record CreateBankApiRequest(String code, String name, Map<String, Object> metadata) {}
