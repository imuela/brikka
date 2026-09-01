package com.brika.platform.document.web;

import java.util.UUID;

/**
 * clientId is optional (BRIKKA V2 I1): when set, the document is attributed to that case holder so
 * it can satisfy a per-holder checklist requirement. Omit it for a document of the expediente.
 */
public record CreateDocumentApiRequest(UUID documentTypeId, UUID clientId) {

  /** Document of the expediente (no holder attribution). */
  public CreateDocumentApiRequest(UUID documentTypeId) {
    this(documentTypeId, null);
  }
}
