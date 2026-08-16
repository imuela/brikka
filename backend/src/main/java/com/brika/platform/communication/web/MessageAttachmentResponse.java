package com.brika.platform.communication.web;

import com.brika.platform.communication.MessageAttachment;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record MessageAttachmentResponse(
    UUID id,
    UUID messageId,
    String originalFilename,
    String mimeType,
    long sizeBytes,
    Instant createdAt,
    URI url) {

  public static MessageAttachmentResponse from(MessageAttachment attachment, URI url) {
    return new MessageAttachmentResponse(
        attachment.id(),
        attachment.messageId(),
        attachment.originalFilename(),
        attachment.mimeType(),
        attachment.sizeBytes(),
        attachment.createdAt(),
        url);
  }
}
