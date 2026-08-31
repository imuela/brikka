package com.brika.platform.casemgmt;

import com.brika.platform.bankrequest.BankOfferRepository;
import com.brika.platform.bankrequest.BankRequestRepository;
import com.brika.platform.bankrequest.FinalFinancing;
import com.brika.platform.bankrequest.FinalFinancingRepository;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.document.CaseChecklist;
import com.brika.platform.document.CaseChecklistService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * BRIKKA V2 I3. Business preconditions for exactly three Case transitions
 * (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §5). The state graph itself stays in {@link
 * CaseWorkflow}; this only adds the "is there enough to proceed?" check on top, invoked by {@link
 * CaseService#changeStatus} and skippable only via an authorized override (permission
 * CASE_TRANSITION_OVERRIDE + mandatory reason, enforced in CaseService/CaseController).
 *
 * <p>Every check is tenant-scoped: it can only see data of {@code theCase}'s own company.
 */
@Component
public class CaseTransitionPreconditions {

  private final CaseChecklistService caseChecklistService;
  private final CaseClientRepository caseClientRepository;
  private final BankRequestRepository bankRequestRepository;
  private final FinalFinancingRepository finalFinancingRepository;
  private final BankOfferRepository bankOfferRepository;

  public CaseTransitionPreconditions(
      CaseChecklistService caseChecklistService,
      CaseClientRepository caseClientRepository,
      BankRequestRepository bankRequestRepository,
      FinalFinancingRepository finalFinancingRepository,
      BankOfferRepository bankOfferRepository) {
    this.caseChecklistService = caseChecklistService;
    this.caseClientRepository = caseClientRepository;
    this.bankRequestRepository = bankRequestRepository;
    this.finalFinancingRepository = finalFinancingRepository;
    this.bankOfferRepository = bankOfferRepository;
  }

  /**
   * Throws {@link ValidationException} (400, stable code per gate) if the transition is not ready.
   */
  public void check(Case theCase, CaseStatus from, CaseStatus to) {
    if (from == CaseStatus.DOCUMENTATION && to == CaseStatus.ANALYSIS) {
      requireMandatoryDocumentsApproved(theCase);
    } else if (from == CaseStatus.BANK_SEARCH && to == CaseStatus.BANK_SUBMISSION) {
      requireAtLeastOneBankRequest(theCase);
    } else if (from == CaseStatus.OFFER && to == CaseStatus.FORMALIZATION) {
      requireSelectedOffer(theCase);
    }
    // Any other transition has no I3 precondition — deliberately untouched.
  }

  /** Gate 1: the case's mandatory document checklist must be fully APPROVED (BRIKKA V2 I1). */
  private void requireMandatoryDocumentsApproved(Case theCase) {
    List<UUID> holderClientIds =
        caseClientRepository.findAllByCaseId(theCase.id()).stream()
            .filter(
                cc ->
                    cc.participationType() == ParticipationType.HOLDER
                        || cc.participationType() == ParticipationType.CO_HOLDER)
            .map(CaseClient::clientId)
            .toList();
    CaseChecklist checklist =
        caseChecklistService.checklist(theCase.id(), theCase.operationType(), holderClientIds);
    if (!checklist.complete()) {
      throw new ValidationException(
          "PRECONDITION_CHECKLIST_INCOMPLETE",
          "All mandatory documents must be uploaded and approved before moving to ANALYSIS ("
              + checklist.mandatoryMissing()
              + " pending).");
    }
  }

  /** Gate 2: at least one bank request must exist for the case (same tenant). */
  private void requireAtLeastOneBankRequest(Case theCase) {
    if (!bankRequestRepository.existsByCaseIdAndCompanyId(theCase.id(), theCase.companyId())) {
      throw new ValidationException(
          "PRECONDITION_NO_BANK_REQUEST",
          "The case needs at least one bank request before moving to BANK_SUBMISSION.");
    }
  }

  /**
   * Gate 3: a selected bank offer (final_financing) must exist for the case (same tenant, same
   * case).
   */
  private void requireSelectedOffer(Case theCase) {
    FinalFinancing finalFinancing =
        finalFinancingRepository
            .findByCaseId(theCase.id())
            .filter(ff -> ff.companyId().equals(theCase.companyId()))
            .orElseThrow(
                () ->
                    new ValidationException(
                        "PRECONDITION_NO_SELECTED_OFFER",
                        "The case needs a selected bank offer before moving to FORMALIZATION."));
    boolean offerBelongsToCase =
        bankOfferRepository.findAllByCaseId(theCase.id()).stream()
            .anyMatch(offer -> offer.id().equals(finalFinancing.bankOfferId()));
    if (!offerBelongsToCase) {
      throw new ValidationException(
          "PRECONDITION_NO_SELECTED_OFFER", "The selected offer does not belong to this case.");
    }
  }
}
