package com.brika.platform.communication.web;

import com.brika.platform.communication.Conversation;
import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
    UUID id, UUID caseId, String type, String status, Instant createdAt, Instant updatedAt) {

  public static ConversationResponse from(Conversation conversation) {
    return new ConversationResponse(
        conversation.id(),
        conversation.caseId(),
        conversation.type(),
        conversation.status(),
        conversation.createdAt(),
        conversation.updatedAt());
  }
}
