package com.brika.platform.communication.web;

import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.communication.Conversation;
import com.brika.platform.communication.ConversationParticipant;
import com.brika.platform.communication.ConversationParticipantRepository;
import com.brika.platform.communication.ConversationRepository;
import com.brika.platform.communication.MessageRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §18, Sprint 7 (D2): minimal internal subset needed for CLIENT
 * conversations to actually work — only type CLIENT is ever created here (validated), never
 * INTERNAL/SYSTEM (Sprint 8 scope, structurally possible in the schema but not reachable through
 * any endpoint in this sprint). TENANT + ROLE/PERMISSION + CASE ASSIGNMENT via CaseAccessService,
 * exactly as every other case-scoped resource since Sprint 3.
 */
@RestController
public class ConversationController {

  private final CaseAccessService caseAccessService;
  private final ConversationRepository conversationRepository;
  private final ConversationParticipantRepository conversationParticipantRepository;
  private final MessageRepository messageRepository;
  private final CaseClientRepository caseClientRepository;

  public ConversationController(
      CaseAccessService caseAccessService,
      ConversationRepository conversationRepository,
      ConversationParticipantRepository conversationParticipantRepository,
      MessageRepository messageRepository,
      CaseClientRepository caseClientRepository) {
    this.caseAccessService = caseAccessService;
    this.conversationRepository = conversationRepository;
    this.conversationParticipantRepository = conversationParticipantRepository;
    this.messageRepository = messageRepository;
    this.caseClientRepository = caseClientRepository;
  }

  @GetMapping("/api/v1/cases/{caseId}/conversations")
  public List<ConversationResponse> list(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CONVERSATION_READ", caseId);
    return conversationRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(ConversationResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/cases/{caseId}/conversations")
  public ConversationResponse create(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody CreateConversationApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "CONVERSATION_CREATE", caseId);

    List<UUID> clientIds = request.clientIds();
    if (clientIds == null || clientIds.isEmpty()) {
      throw new ValidationException(
          "PARTICIPANTS_REQUIRED", "A CLIENT conversation requires at least one participant.");
    }
    for (UUID clientId : clientIds) {
      if (!caseClientRepository.exists(access.theCase().id(), clientId)) {
        throw new ValidationException(
            "CLIENT_NOT_IN_CASE", "Client " + clientId + " is not a participant of this case.");
      }
    }

    UUID conversationId =
        conversationRepository.insert(access.tenantId(), access.theCase().id(), "CLIENT");
    for (UUID clientId : clientIds) {
      conversationParticipantRepository.insertClientParticipant(
          access.tenantId(), conversationId, clientId);
    }

    return ConversationResponse.from(conversationRepository.findById(conversationId).orElseThrow());
  }

  @GetMapping("/api/v1/conversations/{id}/participants")
  public List<ConversationParticipantResponse> listParticipants(
      Authentication authentication, @PathVariable UUID id) {
    Conversation conversation =
        requireAccessibleConversation(authentication, "CONVERSATION_READ", id);
    return conversationParticipantRepository.findActiveByConversationId(conversation.id()).stream()
        .map(ConversationParticipantResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/conversations/{id}/participants")
  public ConversationParticipantResponse addParticipant(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody AddConversationParticipantApiRequest request) {
    Conversation conversation =
        requireAccessibleConversation(authentication, "CONVERSATION_PARTICIPANT_MANAGE", id);

    if (request.clientId() == null) {
      throw new ValidationException("CLIENT_ID_REQUIRED", "clientId is required.");
    }
    if (!caseClientRepository.exists(conversation.caseId(), request.clientId())) {
      throw new ValidationException(
          "CLIENT_NOT_IN_CASE",
          "Client " + request.clientId() + " is not a participant of this case.");
    }

    UUID participantId =
        conversationParticipantRepository.insertClientParticipant(
            conversation.companyId(), conversation.id(), request.clientId());
    return ConversationParticipantResponse.from(
        conversationParticipantRepository.findById(participantId).orElseThrow());
  }

  @DeleteMapping("/api/v1/conversations/{id}/participants/{participantId}")
  public void removeParticipant(
      Authentication authentication, @PathVariable UUID id, @PathVariable UUID participantId) {
    Conversation conversation =
        requireAccessibleConversation(authentication, "CONVERSATION_PARTICIPANT_MANAGE", id);

    ConversationParticipant participant =
        conversationParticipantRepository
            .findById(participantId)
            .filter(p -> p.conversationId().equals(conversation.id()))
            .orElseThrow(
                () -> new ResourceNotFoundException("PARTICIPANT_NOT_FOUND", "Not found."));

    conversationParticipantRepository.remove(participant.id());
  }

  @GetMapping("/api/v1/conversations/{id}/messages")
  public List<MessageResponse> listMessages(Authentication authentication, @PathVariable UUID id) {
    Conversation conversation = requireAccessibleConversation(authentication, "MESSAGE_READ", id);
    return messageRepository.findAllByConversationId(conversation.id()).stream()
        .map(MessageResponse::from)
        .toList();
  }

  @PostMapping("/api/v1/conversations/{id}/messages")
  public MessageResponse createMessage(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CreateMessageApiRequest request) {
    CaseAccessResult access;
    Conversation conversation = requireConversation(id);
    access =
        caseAccessService.requireCaseAccess(authentication, "MESSAGE_SEND", conversation.caseId());
    requireTenantMatch(conversation, access);

    if (request.body() == null || request.body().isBlank()) {
      throw new ValidationException("BODY_REQUIRED", "Message body is required.");
    }

    UUID messageId =
        messageRepository.insertFromUser(conversation.id(), access.user().id(), request.body());
    return MessageResponse.from(messageRepository.findById(messageId).orElseThrow());
  }

  private Conversation requireConversation(UUID id) {
    return conversationRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND", "Not found."));
  }

  private void requireTenantMatch(Conversation conversation, CaseAccessResult access) {
    if (!conversation.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException("CONVERSATION_NOT_FOUND", "Not found.");
    }
  }

  private Conversation requireAccessibleConversation(
      Authentication authentication, String permissionCode, UUID conversationId) {
    Conversation conversation = requireConversation(conversationId);
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, permissionCode, conversation.caseId());
    requireTenantMatch(conversation, access);
    return conversation;
  }
}
