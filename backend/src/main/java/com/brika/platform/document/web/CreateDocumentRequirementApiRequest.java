package com.brika.platform.document.web;

import java.util.Map;
import java.util.UUID;

public record CreateDocumentRequirementApiRequest(
    String operationType, UUID documentTypeId, boolean mandatory, Map<String, Object> conditions) {}
