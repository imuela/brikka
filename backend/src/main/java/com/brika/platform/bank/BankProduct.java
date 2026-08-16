package com.brika.platform.bank;

import java.util.UUID;

public record BankProduct(
    UUID id, UUID bankId, String code, String name, String status, String metadata) {}
