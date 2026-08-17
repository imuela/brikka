package com.brika.platform.ai;

import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 21_AI_V1_SCOPE.md §2.B/C/D: the three synchronous AI use cases — none of them involves the Worker
 * (only document extraction does). Each call is logged to ai_usage regardless of outcome.
 */
@Service
public class AiUseCaseService {

  private final AiProvider aiProvider;
  private final AiUsageRepository aiUsageRepository;

  public AiUseCaseService(AiProvider aiProvider, AiUsageRepository aiUsageRepository) {
    this.aiProvider = aiProvider;
    this.aiUsageRepository = aiUsageRepository;
  }

  public AiProviderResult summarize(UUID companyId, UUID caseId, UUID userId, String context) {
    return logged(companyId, caseId, userId, "SUMMARIZATION", aiProvider.summarize(context));
  }

  public AiProviderResult explain(UUID companyId, UUID caseId, UUID userId, String context) {
    return logged(companyId, caseId, userId, "EXPLANATION", aiProvider.explain(context));
  }

  public AiProviderResult draftMessage(UUID companyId, UUID caseId, UUID userId, String context) {
    return logged(companyId, caseId, userId, "DRAFT_MESSAGE", aiProvider.draftMessage(context));
  }

  private AiProviderResult logged(
      UUID companyId, UUID caseId, UUID userId, String operation, AiProviderResult result) {
    String provider = result.executed() ? "unknown" : "none";
    String model = result.executed() ? "unknown" : "none";
    aiUsageRepository.insert(
        companyId, caseId, userId, provider, model, operation, null, null, null);
    return result;
  }
}
