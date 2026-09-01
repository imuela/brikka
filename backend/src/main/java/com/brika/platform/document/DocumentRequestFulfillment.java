package com.brika.platform.document;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * BRIKKA V2 I1. Reconciles requirement-backed {@link DocumentRequest}s when a document is reviewed:
 * an APPROVED document of a type/holder matching a PENDING request fulfils it; a REJECT of the
 * current version of a previously-approved document reopens it.
 *
 * <p>Isolated in its own collaborator (not inlined in {@link DocumentService} or a controller) on
 * purpose: it is the single "a document satisfies a requirement" decision point, the natural place
 * a future AI classification/extraction step would plug into to propose the (type, holder) link —
 * BRIKKA V2 stays AI-free but AI-ready (SCOPE §5).
 */
@Component
public class DocumentRequestFulfillment {

  private final DocumentRequestRepository documentRequestRepository;

  public DocumentRequestFulfillment(DocumentRequestRepository documentRequestRepository) {
    this.documentRequestRepository = documentRequestRepository;
  }

  /** Called after {@link DocumentService#review} has persisted the new document status. */
  public void onDocumentReviewed(Document document, ReviewStatus decision) {
    List<DocumentRequest> matching =
        documentRequestRepository.findByCaseTypeAndClientForRequirements(
            document.caseId(), document.documentTypeId(), document.clientId());
    for (DocumentRequest request : matching) {
      if (decision == ReviewStatus.APPROVED && request.status() == DocumentRequestStatus.PENDING) {
        documentRequestRepository.updateStatus(request.id(), DocumentRequestStatus.FULFILLED);
      } else if (decision == ReviewStatus.REJECTED
          && request.status() == DocumentRequestStatus.FULFILLED) {
        documentRequestRepository.updateStatus(request.id(), DocumentRequestStatus.PENDING);
      }
    }
  }
}
