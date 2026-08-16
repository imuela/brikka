package com.brika.platform.bank.web;

import java.util.Map;
import java.util.UUID;

public record BankResponse(
    UUID id, String code, String name, String status, Map<String, Object> metadata) {}
