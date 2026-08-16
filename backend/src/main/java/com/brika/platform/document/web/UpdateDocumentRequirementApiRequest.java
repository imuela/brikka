package com.brika.platform.document.web;

import java.util.Map;

public record UpdateDocumentRequirementApiRequest(
    boolean mandatory, boolean active, Map<String, Object> conditions) {}
