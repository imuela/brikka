package com.brika.platform.bankrequest.web;

import com.brika.platform.bankrequest.BankOffer;
import com.brika.platform.bankrequest.BankOfferRepository;
import com.brika.platform.bankrequest.BankRequest;
import com.brika.platform.bankrequest.BankRequestRepository;
import com.brika.platform.bankrequest.FinalFinancing;
import com.brika.platform.bankrequest.FinalFinancingRepository;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §15, Sprint 6A. Access to a standalone bank-offer is derived
 * through two hops (offer -> bank_request -> case), mirroring the single-hop pattern from
 * DocumentAccessService (Sprint 4) / FinancingRequestController (Sprint 5). Decision D3 (Option 2):
 * selecting an offer upserts the case's single final_financing row instead of creating a second one
 * — never touches the status of other offers for the case (no such state is documented). Decision
 * D4 (Option 1): no dedicated GET endpoint for final_financing — /select is the only way to read
 * it.
 */
@RestController
public class BankOfferController {

  private final CaseAccessService caseAccessService;
  private final BankOfferRepository bankOfferRepository;
  private final BankRequestRepository bankRequestRepository;
  private final FinalFinancingRepository finalFinancingRepository;
  private final ObjectMapper objectMapper;

  public BankOfferController(
      CaseAccessService caseAccessService,
      BankOfferRepository bankOfferRepository,
      BankRequestRepository bankRequestRepository,
      FinalFinancingRepository finalFinancingRepository,
      ObjectMapper objectMapper) {
    this.caseAccessService = caseAccessService;
    this.bankOfferRepository = bankOfferRepository;
    this.bankRequestRepository = bankRequestRepository;
    this.finalFinancingRepository = finalFinancingRepository;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/api/v1/cases/{caseId}/offers")
  public List<BankOfferResponse> list(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "BANK_OFFER_READ", caseId);
    return bankOfferRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(this::toResponse)
        .toList();
  }

  @GetMapping("/api/v1/bank-offers/{id}")
  public BankOfferResponse get(Authentication authentication, @PathVariable UUID id) {
    return toResponse(requireAccessibleOffer(authentication, "BANK_OFFER_READ", id).offer());
  }

  @PostMapping("/api/v1/bank-offers/{id}/select")
  public FinalFinancingResponse select(Authentication authentication, @PathVariable UUID id) {
    OfferAccess offerAccess = requireAccessibleOffer(authentication, "BANK_OFFER_SELECT", id);
    UUID tenantId = offerAccess.access().tenantId();
    UUID caseId = offerAccess.bankRequest().caseId();

    FinalFinancing finalFinancing =
        finalFinancingRepository
            .findByCaseId(caseId)
            .map(
                existing -> {
                  finalFinancingRepository.updateBankOffer(existing.id(), offerAccess.offer().id());
                  return finalFinancingRepository.findById(existing.id()).orElseThrow();
                })
            .orElseGet(
                () -> {
                  UUID newId =
                      finalFinancingRepository.insert(tenantId, caseId, offerAccess.offer().id());
                  return finalFinancingRepository.findById(newId).orElseThrow();
                });

    return toResponse(finalFinancing);
  }

  private record OfferAccess(CaseAccessResult access, BankRequest bankRequest, BankOffer offer) {}

  private OfferAccess requireAccessibleOffer(
      Authentication authentication, String permissionCode, UUID offerId) {
    BankOffer offer =
        bankOfferRepository
            .findById(offerId)
            .orElseThrow(() -> new ResourceNotFoundException("BANK_OFFER_NOT_FOUND", "Not found."));

    BankRequest bankRequest =
        bankRequestRepository
            .findById(offer.bankRequestId())
            .orElseThrow(() -> new ResourceNotFoundException("BANK_OFFER_NOT_FOUND", "Not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, permissionCode, bankRequest.caseId());
    if (!offer.companyId().equals(access.tenantId())
        || !bankRequest.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException("BANK_OFFER_NOT_FOUND", "Not found.");
    }
    return new OfferAccess(access, bankRequest, offer);
  }

  private BankOfferResponse toResponse(BankOffer offer) {
    return new BankOfferResponse(
        offer.id(),
        offer.bankRequestId(),
        offer.bankId(),
        offer.status(),
        offer.amount(),
        offer.interestRate(),
        offer.termMonths(),
        offer.payment(),
        readJson(offer.conditions()),
        offer.receivedAt(),
        offer.createdAt(),
        offer.updatedAt());
  }

  private FinalFinancingResponse toResponse(FinalFinancing finalFinancing) {
    return new FinalFinancingResponse(
        finalFinancing.id(),
        finalFinancing.caseId(),
        finalFinancing.bankOfferId(),
        finalFinancing.status(),
        finalFinancing.finalizedAt(),
        finalFinancing.createdAt(),
        finalFinancing.updatedAt());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> readJson(String json) {
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of();
    }
  }
}
