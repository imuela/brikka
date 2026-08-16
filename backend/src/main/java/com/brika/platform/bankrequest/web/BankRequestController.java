package com.brika.platform.bankrequest.web;

import com.brika.platform.activity.ActivityPublisher;
import com.brika.platform.activity.CaseActivityEvent;
import com.brika.platform.bank.Bank;
import com.brika.platform.bank.BankContact;
import com.brika.platform.bank.BankContactRepository;
import com.brika.platform.bank.BankRepository;
import com.brika.platform.bankrequest.BankOffer;
import com.brika.platform.bankrequest.BankOfferRepository;
import com.brika.platform.bankrequest.BankRequest;
import com.brika.platform.bankrequest.BankRequestRepository;
import com.brika.platform.bankrequest.BankResponse;
import com.brika.platform.bankrequest.BankResponseRepository;
import com.brika.platform.casemgmt.CaseAccessResult;
import com.brika.platform.casemgmt.CaseAccessService;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §14, Sprint 6A (Decision D1: matching engine explicitly out of
 * scope). TENANT + ROLE/PERMISSION + CASE ASSIGNMENT via CaseAccessService, exactly as Sprints 3-5.
 * contact_snapshot is captured once at creation from the current BankContact state, so later edits
 * to the contact never alter the historical request (06_BANK_ENGINE_SPECIFICATION.md §7).
 */
@RestController
public class BankRequestController {

  private final CaseAccessService caseAccessService;
  private final BankRequestRepository bankRequestRepository;
  private final BankResponseRepository bankResponseRepository;
  private final BankOfferRepository bankOfferRepository;
  private final BankRepository bankRepository;
  private final BankContactRepository bankContactRepository;
  private final ActivityPublisher activityPublisher;
  private final ObjectMapper objectMapper;

  public BankRequestController(
      CaseAccessService caseAccessService,
      BankRequestRepository bankRequestRepository,
      BankResponseRepository bankResponseRepository,
      BankOfferRepository bankOfferRepository,
      BankRepository bankRepository,
      BankContactRepository bankContactRepository,
      ActivityPublisher activityPublisher,
      ObjectMapper objectMapper) {
    this.caseAccessService = caseAccessService;
    this.bankRequestRepository = bankRequestRepository;
    this.bankResponseRepository = bankResponseRepository;
    this.bankOfferRepository = bankOfferRepository;
    this.bankRepository = bankRepository;
    this.bankContactRepository = bankContactRepository;
    this.activityPublisher = activityPublisher;
    this.objectMapper = objectMapper;
  }

  @GetMapping("/api/v1/cases/{caseId}/bank-requests")
  public List<BankRequestResponse> list(Authentication authentication, @PathVariable UUID caseId) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "BANK_REQUEST_READ", caseId);
    return bankRequestRepository.findAllByCaseId(access.theCase().id()).stream()
        .map(this::toResponse)
        .toList();
  }

  @PostMapping("/api/v1/cases/{caseId}/bank-requests")
  public BankRequestResponse create(
      Authentication authentication,
      @PathVariable UUID caseId,
      @RequestBody CreateBankRequestApiRequest request) {
    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, "BANK_REQUEST_CREATE", caseId);

    Bank bank =
        bankRepository
            .findById(request.bankId())
            .orElseThrow(() -> new ValidationException("BANK_NOT_FOUND", "Bank not found."));

    String contactSnapshotJson = "{}";
    if (request.bankContactId() != null) {
      BankContact contact =
          bankContactRepository
              .findById(request.bankContactId())
              .filter(c -> access.tenantId().equals(c.companyId()))
              .orElseThrow(
                  () ->
                      new ValidationException("BANK_CONTACT_NOT_FOUND", "Bank contact not found."));
      contactSnapshotJson = writeJson(snapshotOf(contact));
    }

    UUID id =
        bankRequestRepository.insert(
            access.tenantId(),
            access.theCase().id(),
            bank.id(),
            request.bankContactId(),
            contactSnapshotJson);

    activityPublisher.publish(
        new CaseActivityEvent(
            "bank.request.created",
            access.tenantId(),
            access.theCase().id(),
            access.user().id(),
            "Bank request sent to " + bank.name()));

    return toResponse(bankRequestRepository.findById(id).orElseThrow());
  }

  @GetMapping("/api/v1/bank-requests/{id}")
  public BankRequestResponse get(Authentication authentication, @PathVariable UUID id) {
    return toResponse(
        requireAccessibleBankRequest(authentication, "BANK_REQUEST_READ", id).bankRequest());
  }

  @PostMapping("/api/v1/bank-requests/{id}/responses")
  public BankResponseResponse createResponse(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CreateBankResponseApiRequest request) {
    BankRequestAccess access =
        requireAccessibleBankRequest(authentication, "BANK_RESPONSE_REGISTER", id);

    UUID responseId =
        bankResponseRepository.insert(id, request.summary(), writeJson(request.payload()));

    activityPublisher.publish(
        new CaseActivityEvent(
            "bank.response.received",
            access.access().tenantId(),
            access.bankRequest().caseId(),
            access.access().user().id(),
            "Bank response received for request " + id));

    return toResponse(bankResponseRepository.findById(responseId).orElseThrow());
  }

  @PostMapping("/api/v1/bank-requests/{id}/offers")
  public BankOfferResponse createOffer(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody CreateBankOfferApiRequest request) {
    BankRequestAccess access =
        requireAccessibleBankRequest(authentication, "BANK_OFFER_CREATE", id);
    BankRequest bankRequest = access.bankRequest();

    UUID offerId =
        bankOfferRepository.insert(
            access.access().tenantId(),
            bankRequest.id(),
            bankRequest.bankId(),
            request.amount(),
            request.interestRate(),
            request.termMonths(),
            request.payment(),
            writeJson(request.conditions()));

    return toResponse(bankOfferRepository.findById(offerId).orElseThrow());
  }

  private record BankRequestAccess(CaseAccessResult access, BankRequest bankRequest) {}

  private BankRequestAccess requireAccessibleBankRequest(
      Authentication authentication, String permissionCode, UUID bankRequestId) {
    BankRequest bankRequest =
        bankRequestRepository
            .findById(bankRequestId)
            .orElseThrow(
                () -> new ResourceNotFoundException("BANK_REQUEST_NOT_FOUND", "Not found."));

    CaseAccessResult access =
        caseAccessService.requireCaseAccess(authentication, permissionCode, bankRequest.caseId());
    if (!bankRequest.companyId().equals(access.tenantId())) {
      throw new ResourceNotFoundException("BANK_REQUEST_NOT_FOUND", "Not found.");
    }
    return new BankRequestAccess(access, bankRequest);
  }

  private Map<String, Object> snapshotOf(BankContact contact) {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("name", contact.name());
    snapshot.put("position", contact.position());
    snapshot.put("department", contact.department());
    snapshot.put("branch", contact.branch());
    snapshot.put("email", contact.email());
    snapshot.put("phone", contact.phone());
    snapshot.put("secondaryPhone", contact.secondaryPhone());
    return snapshot;
  }

  private BankRequestResponse toResponse(BankRequest bankRequest) {
    return new BankRequestResponse(
        bankRequest.id(),
        bankRequest.caseId(),
        bankRequest.bankId(),
        bankRequest.bankContactId(),
        bankRequest.status(),
        bankRequest.submittedAt(),
        readJson(bankRequest.contactSnapshot()),
        bankRequest.createdAt(),
        bankRequest.updatedAt());
  }

  private BankResponseResponse toResponse(BankResponse bankResponse) {
    return new BankResponseResponse(
        bankResponse.id(),
        bankResponse.bankRequestId(),
        bankResponse.status(),
        bankResponse.receivedAt(),
        bankResponse.summary(),
        readJson(bankResponse.payload()),
        bankResponse.createdAt());
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

  private String writeJson(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value == null ? Map.of() : value);
    } catch (JsonProcessingException e) {
      throw new ValidationException("INVALID_JSON", "Value could not be serialized.");
    }
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
