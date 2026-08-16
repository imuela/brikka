package com.brika.platform.portal.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.communication.Conversation;
import com.brika.platform.communication.ConversationParticipantRepository;
import com.brika.platform.communication.ConversationRepository;
import com.brika.platform.communication.Message;
import com.brika.platform.communication.MessageAttachment;
import com.brika.platform.communication.MessageAttachmentRepository;
import com.brika.platform.communication.MessageAttachmentService;
import com.brika.platform.communication.MessageRepository;
import com.brika.platform.communication.web.CreateMessageApiRequest;
import com.brika.platform.communication.web.MessageAttachmentResponse;
import com.brika.platform.communication.web.MessageResponse;
import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.portal.PortalAuthorizationService;
import com.brika.platform.portal.PortalCaseAccessResult;
import com.brika.platform.portal.PortalCaseAccessService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 17_API_SPECIFICATION_DETAILED.md §19, ADR-COMMS-002 (tenant + case + participant + visibility,
 * always evaluated in backend). "The" conversation for a case is the most recently created CLIENT
 * conversation where this client is an active participant — the API takes no conversationId, and
 * nothing documents what happens with more than one, so this is the simplest, most conservative
 * resolution (Sprint 7 gate review interpretation, non-blocking).
 */
@RestController
public class PortalMessageController {

  private final PortalAuthorizationService portalAuthorizationService;
  private final PortalCaseAccessService portalCaseAccessService;
  private final ConversationRepository conversationRepository;
  private final ConversationParticipantRepository conversationParticipantRepository;
  private final MessageRepository messageRepository;
  private final MessageAttachmentRepository messageAttachmentRepository;
  private final MessageAttachmentService messageAttachmentService;

  public PortalMessageController(
      PortalAuthorizationService portalAuthorizationService,
      PortalCaseAccessService portalCaseAccessService,
      ConversationRepository conversationRepository,
      ConversationParticipantRepository conversationParticipantRepository,
      MessageRepository messageRepository,
      MessageAttachmentRepository messageAttachmentRepository,
      MessageAttachmentService messageAttachmentService) {
    this.portalAuthorizationService = portalAuthorizationService;
    this.portalCaseAccessService = portalCaseAccessService;
    this.conversationRepository = conversationRepository;
    this.conversationParticipantRepository = conversationParticipantRepository;
    this.messageRepository = messageRepository;
    this.messageAttachmentRepository = messageAttachmentRepository;
    this.messageAttachmentService = messageAttachmentService;
  }

  @GetMapping("/api/v1/portal/cases/{id}/messages")
  public List<MessageResponse> list(Authentication authentication, @PathVariable UUID id) {
    PortalCaseAccessResult access =
        portalCaseAccessService.requireCaseAccess(authentication, "PORTAL_MESSAGE_READ", id);
    Optional<Conversation> conversation = resolveConversation(access);
    if (conversation.isEmpty()) {
      return List.of();
    }
    return messageRepository.findAllByConversationId(conversation.get().id()).stream()
        .map(MessageResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/portal/cases/{id}/messages")
  public MessageResponse create(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CreateMessageApiRequest request) {
    PortalCaseAccessResult access =
        portalCaseAccessService.requireCaseAccess(authentication, "PORTAL_MESSAGE_SEND", id);
    Conversation conversation =
        resolveConversation(access)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "CONVERSATION_NOT_FOUND", "No conversation available for this case yet."));

    if (request.body() == null || request.body().isBlank()) {
      throw new ValidationException("BODY_REQUIRED", "Message body is required.");
    }

    UUID messageId =
        messageRepository.insertFromClient(
            conversation.id(), access.account().clientId(), request.body());
    return MessageResponse.from(messageRepository.findById(messageId).orElseThrow());
  }

  @PostMapping("/api/v1/portal/messages/{messageId}/attachments")
  public MessageAttachmentResponse uploadAttachment(
      Authentication authentication,
      @PathVariable UUID messageId,
      @RequestParam("file") MultipartFile file) {
    MessageAccess access =
        requireAccessibleMessage(authentication, "PORTAL_MESSAGE_ATTACHMENT_UPLOAD", messageId);

    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    MessageAttachment attachment =
        messageAttachmentService.upload(
            access.conversation().companyId(),
            access.message().id(),
            content,
            file.getOriginalFilename(),
            file.getContentType());
    return MessageAttachmentResponse.from(
        attachment, messageAttachmentService.presignedDownloadUrl(attachment));
  }

  @GetMapping("/api/v1/portal/messages/{messageId}/attachments")
  public List<MessageAttachmentResponse> listAttachments(
      Authentication authentication, @PathVariable UUID messageId) {
    MessageAccess access =
        requireAccessibleMessage(authentication, "PORTAL_MESSAGE_READ", messageId);
    return messageAttachmentRepository.findAllByMessageId(access.message().id()).stream()
        .map(
            a ->
                MessageAttachmentResponse.from(a, messageAttachmentService.presignedDownloadUrl(a)))
        .toList();
  }

  private Optional<Conversation> resolveConversation(PortalCaseAccessResult access) {
    ClientPortalAccount account = access.account();
    return conversationRepository.findAllByCaseId(access.theCase().id()).stream()
        .filter(c -> "CLIENT".equals(c.type()))
        .filter(
            c ->
                conversationParticipantRepository.hasActiveClientParticipant(
                    c.id(), account.clientId()))
        .findFirst();
  }

  private record MessageAccess(
      ClientPortalAccount account, Conversation conversation, Message message) {}

  private MessageAccess requireAccessibleMessage(
      Authentication authentication, String permissionCode, UUID messageId) {
    portalAuthorizationService.requirePermission(authentication, permissionCode);
    ClientPortalAccount account = portalAuthorizationService.currentAccount(authentication);

    Message message =
        messageRepository
            .findById(messageId)
            .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND", "Not found."));
    Conversation conversation =
        conversationRepository
            .findById(message.conversationId())
            .filter(c -> "CLIENT".equals(c.type()))
            .filter(c -> account.companyId().equals(c.companyId()))
            .filter(
                c ->
                    conversationParticipantRepository.hasActiveClientParticipant(
                        c.id(), account.clientId()))
            .orElseThrow(() -> new ResourceNotFoundException("MESSAGE_NOT_FOUND", "Not found."));

    return new MessageAccess(account, conversation, message);
  }
}
