package com.brika.platform.identity;

import java.util.UUID;

public record Permission(UUID id, String code, String name) {}
