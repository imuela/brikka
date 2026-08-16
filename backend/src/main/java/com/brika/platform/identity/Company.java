package com.brika.platform.identity;

import java.util.UUID;

public record Company(UUID id, String legalName, String tradeName, String taxId, String status) {}
