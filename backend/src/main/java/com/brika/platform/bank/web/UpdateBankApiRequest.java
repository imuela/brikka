package com.brika.platform.bank.web;

import java.util.Map;

public record UpdateBankApiRequest(String name, String status, Map<String, Object> metadata) {}
