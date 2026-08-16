package com.brika.platform.bank.web;

import java.util.Map;
import java.util.UUID;

public record BankProductResponse(
    UUID id, UUID bankId, String code, String name, String status, Map<String, Object> metadata) {}
