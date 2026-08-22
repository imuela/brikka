package com.brika.platform.ai;

import com.brika.platform.casemgmt.CaseClient;
import com.brika.platform.casemgmt.CaseClientRepository;
import com.brika.platform.crm.ClientFinancialProfile;
import com.brika.platform.crm.ClientFinancialProfileRepository;
import com.brika.platform.document.Document;
import com.brika.platform.document.DocumentRepository;
import com.brika.platform.document.DocumentVersion;
import com.brika.platform.document.DocumentVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies the outcome of a document extraction attempt — invoked either in-process by
 * LocalAiTaskDispatcher (synchronously, no network) or by the internal worker callback controller
 * (real HTTP path). Both converge on the same persistence + traceability logic, so behavior is
 * identical regardless of transport (D10-5).
 *
 * <p>Sprint 33: {@code status} is no longer unconditionally NO_PROVIDER. Three honest, mutually
 * exclusive outcomes now exist — NO_PROVIDER (nothing attempted), FAILED (a real provider was
 * attempted but produced nothing usable), COMPLETED (a real provider produced a usable result) —
 * see the outcome-selection comment inside {@link #applyResult} for the full rule. D10-2's original
 * no-op shape is left byte-for-byte unchanged for the NO_PROVIDER case.
 */
@Component
public class DocumentExtractionResultHandler {

  private static final String FIELD_MONTHLY_INCOME = "monthly_income";
  private static final BigDecimal INCONSISTENCY_TOLERANCE = BigDecimal.valueOf(50);

  private final DocumentExtractionRepository documentExtractionRepository;
  private final DocumentVersionRepository documentVersionRepository;
  private final DocumentRepository documentRepository;
  private final CaseClientRepository caseClientRepository;
  private final ClientFinancialProfileRepository clientFinancialProfileRepository;
  private final AiUsageRepository aiUsageRepository;
  private final ObjectMapper objectMapper;

  public DocumentExtractionResultHandler(
      DocumentExtractionRepository documentExtractionRepository,
      DocumentVersionRepository documentVersionRepository,
      DocumentRepository documentRepository,
      CaseClientRepository caseClientRepository,
      ClientFinancialProfileRepository clientFinancialProfileRepository,
      AiUsageRepository aiUsageRepository,
      ObjectMapper objectMapper) {
    this.documentExtractionRepository = documentExtractionRepository;
    this.documentVersionRepository = documentVersionRepository;
    this.documentRepository = documentRepository;
    this.caseClientRepository = caseClientRepository;
    this.clientFinancialProfileRepository = clientFinancialProfileRepository;
    this.aiUsageRepository = aiUsageRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void applyResult(
      UUID extractionId,
      List<Map<String, Object>> extractedFields,
      Map<String, Object> confidence,
      String provider,
      String model,
      String summary,
      List<String> warnings) {
    List<Map<String, Object>> fields = extractedFields == null ? List.of() : extractedFields;
    boolean providerAttempted = provider != null && !provider.isBlank();
    boolean producedUsableResult = !fields.isEmpty() || (summary != null && !summary.isBlank());

    UUID caseId = resolveCaseId(extractionId);

    // Three honest, mutually exclusive outcomes (Sprint 33 extends D10-2's honest-no-op principle
    // symmetrically, never claiming more OR less happened than actually did):
    //  - NO_PROVIDER: nothing was ever attempted (unconfigured Worker, or LocalAiTaskDispatcher).
    //    D10-2's original shape is left completely unchanged here — still just the raw
    //    fields/confidence, exactly as before Sprint 33.
    //  - FAILED: a real provider was attempted but produced nothing usable (timeout, invalid
    //    response, etc.) — distinct from NO_PROVIDER so a broker can tell "not configured" apart
    //    from "configured but broke", per the sprint's own error-handling requirements.
    //  - COMPLETED: a real provider produced a usable result.
    Object extractedDataToStore = fields;
    String resolvedStatus;
    if (!providerAttempted) {
      resolvedStatus = "NO_PROVIDER";
    } else if (!producedUsableResult) {
      resolvedStatus = "FAILED";
      Map<String, Object> wrapper = new LinkedHashMap<>();
      wrapper.put("fields", fields);
      wrapper.put("summary", summary);
      wrapper.put("warnings", warnings == null ? List.of() : warnings);
      extractedDataToStore = wrapper;
    } else {
      resolvedStatus = "COMPLETED";
      List<Map<String, Object>> inconsistencies =
          caseId != null ? detectInconsistencies(caseId, fields) : List.of();
      Map<String, Object> wrapper = new LinkedHashMap<>();
      wrapper.put("fields", fields);
      wrapper.put("summary", summary);
      wrapper.put("warnings", warnings == null ? List.of() : warnings);
      wrapper.put("inconsistencies", inconsistencies);
      extractedDataToStore = wrapper;
    }

    String resolvedProvider = providerAttempted ? provider : "none";
    String resolvedModel = providerAttempted && model != null ? model : "none";

    documentExtractionRepository.applyResult(
        extractionId,
        resolvedStatus,
        resolvedProvider,
        resolvedModel,
        toJson(extractedDataToStore),
        toJson(confidence));

    if (caseId != null) {
      aiUsageRepository.insert(
          documentExtractionRepository.findById(extractionId).orElseThrow().companyId(),
          caseId,
          null, // requester attribution not available on the async worker callback path
          resolvedProvider,
          resolvedModel,
          "DOCUMENT_EXTRACTION",
          null,
          null,
          null);
    }
  }

  /**
   * Sprint 33: compares a recognized financial field extracted from the document against the
   * document's case clients' already-stored ClientFinancialProfile — never decides which value is
   * correct, only flags a difference for a human to resolve. Deliberately minimal: monthly_income
   * is the one field this sprint's own worked example names (a documented, demonstrable case rather
   * than a general-purpose field-matching engine — see 21_AI_V1_SCOPE.md §2.B "ejemplo
   * conceptual").
   */
  private List<Map<String, Object>> detectInconsistencies(
      UUID caseId, List<Map<String, Object>> fields) {
    BigDecimal detectedIncome = extractNumericField(fields, FIELD_MONTHLY_INCOME);
    if (detectedIncome == null) {
      return List.of();
    }
    List<Map<String, Object>> inconsistencies = new ArrayList<>();
    for (CaseClient caseClient : caseClientRepository.findAllByCaseId(caseId)) {
      clientFinancialProfileRepository
          .findByClientId(caseClient.clientId())
          .map(ClientFinancialProfile::monthlyIncome)
          .filter(profileIncome -> profileIncome != null)
          .filter(
              profileIncome ->
                  profileIncome.subtract(detectedIncome).abs().compareTo(INCONSISTENCY_TOLERANCE)
                      > 0)
          .ifPresent(
              profileIncome -> {
                Map<String, Object> inconsistency = new LinkedHashMap<>();
                inconsistency.put("field", FIELD_MONTHLY_INCOME);
                inconsistency.put("clientId", caseClient.clientId());
                inconsistency.put("profileValue", profileIncome);
                inconsistency.put("documentValue", detectedIncome);
                inconsistencies.add(inconsistency);
              });
    }
    return inconsistencies;
  }

  private BigDecimal extractNumericField(List<Map<String, Object>> fields, String name) {
    for (Map<String, Object> field : fields) {
      if (name.equals(field.get("name")) && field.get("value") != null) {
        try {
          return new BigDecimal(field.get("value").toString());
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return null;
  }

  private UUID resolveCaseId(UUID extractionId) {
    DocumentExtraction extraction =
        documentExtractionRepository.findById(extractionId).orElseThrow();
    DocumentVersion version =
        documentVersionRepository.findById(extraction.documentVersionId()).orElseThrow();
    Document document = documentRepository.findById(version.documentId()).orElseThrow();
    return document.caseId();
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize document extraction result", e);
    }
  }
}
