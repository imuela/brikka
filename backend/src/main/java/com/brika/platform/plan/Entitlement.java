package com.brika.platform.plan;

import java.util.UUID;

public record Entitlement(
    UUID id, String code, String name, String description, EntitlementValueType valueType) {}
