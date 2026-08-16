package com.brika.platform.communication.web;

import com.brika.platform.communication.ConversationParticipant;
import java.time.Instant;
import java.util.UUID;

public record ConversationParticipantResponse(
    UUID id, UUID conversationId, UUID clientId, Instant createdAt) {

  public static ConversationParticipantResponse from(ConversationParticipant participant) {
    return new ConversationParticipantResponse(
        participant.id(),
        participant.conversationId(),
        participant.participantClientId(),
        participant.createdAt());
  }
}
