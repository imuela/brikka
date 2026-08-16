package com.brika.platform.communication.web;

import com.brika.platform.communication.Message;
import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
    UUID id,
    UUID conversationId,
    UUID senderUserId,
    UUID senderClientId,
    String body,
    Instant createdAt,
    Instant editedAt) {

  public static MessageResponse from(Message message) {
    return new MessageResponse(
        message.id(),
        message.conversationId(),
        message.senderUserId(),
        message.senderClientId(),
        message.body(),
        message.createdAt(),
        message.editedAt());
  }
}
