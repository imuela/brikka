package com.brika.platform.document.web;

import java.time.Instant;
import java.util.UUID;

public record CreateDocumentRequestApiRequest(
    UUID documentTypeId, UUID requestedFromClientId, Instant dueAt, UUID requirementId) {}
