package com.brika.platform.communication;

import java.time.Instant;
import java.util.UUID;

/**
 * ADR-COMMS-002: mandatory for CLIENT-type conversations. Exactly one of participantUserId /
 * participantClientId is set (chk_conversation_participants_single_participant).
 */
public record ConversationParticipant(
    UUID id,
    UUID companyId,
    UUID conversationId,
    UUID participantUserId,
    UUID participantClientId,
    Instant createdAt,
    Instant removedAt) {}
