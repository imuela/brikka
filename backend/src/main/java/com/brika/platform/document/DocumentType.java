package com.brika.platform.document;

import java.util.UUID;

public record DocumentType(UUID id, String code, String name, boolean active) {}
