package com.brika.platform.communication.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.communication.Conversation;
import com.brika.platform.communication.ConversationRepository;
import com.brika.platform.communication.Message;
import com.brika.platform.communication.MessageAttachment;
import com.brika.platform.communication.MessageAttachmentRepository;
import com.brika.platform.communication.MessageAttachmentService;
import com.brika.platform.communication.MessageRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 17_API_SPECIFICATION_DETAILED.md §18. Access derived through two hops (message -> conversation ->
 * case), mirroring the bank-offer pattern from Sprint 6A.
 */
@RestController
public class MessageAttachmentController {

  private final CaseAccessService caseAccessService;
  private final MessageRepository messageRepository;
  private final ConversationRepository conversationRepository;
  private final MessageAttachmentRepository messageAttachmentRepository;
  private final MessageAttachmentService messageAttachmentService;

  public MessageAttachmentController(
      CaseAccessService caseAccessService,
      MessageRepository messageRepository,
      ConversationRepository conversationRepository,
      MessageAttachmentRepository messageAttachmentRepository,
      MessageAttachmentService messageAttachmentService) {
    this.caseAccessService = caseAccessService;
    this.messageRepository = messageRepository;
    this.conversationRepository = conversationRepository;
    this.messageAttachmentRepository = messageAttachmentRepository;
    this.messageAttachmentService = messageAttachmentService;
  }

  @PostMapping("/api/v1/messages/{id}/attachments")
  public MessageAttachmentResponse upload(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file) {
    MessageAccess access =
        requireAccessibleMessage(authentication, "MESSAGE_ATTACHMENT_UPLOAD", id);

    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    MessageAttachment attachment =
        messageAttachmentService.upload(
            access.access().tenantId(),
            access.message().id(),
            content,
            file.getOriginalFilename(),
            file.getContentType());
    return MessageAttachmentResponse.from(
        attachment, messageAttachmentService.presignedDownloadUrl(attachment));
  }

  @GetMapping("/api/v1/messages/{id}/attachments")
  public List<MessageAttachmentResponse> list(
      Authentication authentication, @PathVariable UUID id) {
    MessageAccess access =
        requireAccessibleMessage(authentication, "MESSAGE_ATTACHMENT_DOWNLOAD", id);
    return messageAttachmentRepository.findAllByMessageId(access.message().id()).stream()
        .map(
            a ->
                MessageAttachmentResponse.from(a, messageAttachmentService.presignedDownloadUrl(a)))
        .toList();
  }

  private record MessageAccess(
      CaseAccessResult access, Conversation conversation, Message message) {}

  private MessageAccess requireAccessibleMessage(
      Authentication authentication, String permissionCode, UUID messageId) {
    Message message =
        messageRepository
            .findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND", "Not found."));
    Conversation conversation =
        conversationRepository
            .findById(message.conversationId())
            .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND", "Not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, permissionCode, conversation.caseId());
    if (!conversation.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException("MESSAGE_NOT_FOUND", "Not found.");
    }
    return new MessageAccess(access, conversation, message);
  }
}
