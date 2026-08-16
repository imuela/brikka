package com.brika.platform.document;

import java.util.UUID;

/** conditions is raw JSON text (document_requirements.conditions is jsonb). ADR-DOC-001. */
public record DocumentRequirement(
    UUID id,
    String operationType,
    UUID documentTypeId,
    boolean mandatory,
    String conditions,
    boolean active) {}
