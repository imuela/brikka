package com.brika.platform.communication;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-COMMS-001: reuses message_attachments for Portal Cliente conversations too — never becomes a
 * DOCUMENT of the formal pipeline (07_PORTAL_CLIENTE.md).
 */
public record MessageAttachment(
    UUID id,
    UUID companyId,
    UUID messageId,
    String storageKey,
    String originalFilename,
    String mimeType,
    long sizeBytes,
    String checksum,
    Instant createdAt) {}
