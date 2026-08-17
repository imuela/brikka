package com.brika.platform.ai.web;

import com.brika.platform.ai.AiUseCaseService;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.communication.Conversation;
import com.brika.platform.communication.ConversationRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 21_AI_V1_SCOPE.md §2.D. Access is derived: conversation -> case, gated by AI_DRAFT_MESSAGE
 * (D10-1). The draft is returned synchronously in the response body and is NEVER persisted as a
 * real Message — sending remains the pre-existing, human-triggered POST
 * /conversations/{id}/messages, entirely untouched by this endpoint.
 */
@RestController
public class AiDraftMessageController {

  private final CaseAccessService caseAccessService;
  private final AiUseCaseService aiUseCaseService;
  private final ConversationRepository conversationRepository;

  public AiDraftMessageController(
      CaseAccessService caseAccessService,
      AiUseCaseService aiUseCaseService,
      ConversationRepository conversationRepository) {
    this.caseAccessService = caseAccessService;
    this.aiUseCaseService = aiUseCaseService;
    this.conversationRepository = conversationRepository;
  }

  @PostMapping("/api/v1/conversations/{id}/ai/draft-message")
  public AiUseCaseResponse draftMessage(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody AiUseCaseApiRequest request) {
    Conversation conversation =
        conversationRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("CONVERSATION_NOT_FOUND", "Not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(
            authentication, "AI_DRAFT_MESSAGE", conversation.caseId());

    return AiUseCaseResponse.from(
        aiUseCaseService.draftMessage(
            access.tenantId(), access.theCase().id(), access.user().id(), request.context()));
  }
}
