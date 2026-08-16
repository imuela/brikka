package com.brika.platform.plan;

import java.util.UUID;

/** Global catalog, not tenant-owned (ADR-PLATFORM-001). */
public record Plan(UUID id, String code, String name, String status) {}
