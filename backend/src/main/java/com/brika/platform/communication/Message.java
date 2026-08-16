package com.brika.platform.communication;

import java.time.Instant;
import java.util.UUID;

/** Exactly one of senderUserId / senderClientId is set (chk_messages_single_sender). */
public record Message(
    UUID id,
    UUID conversationId,
    UUID senderUserId,
    UUID senderClientId,
    String body,
    Instant createdAt,
    Instant editedAt) {}
