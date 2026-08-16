package com.brika.platform.document.web;

/** decision must be APPROVED or REJECTED. "Request a new version" is REJECTED + comment. */
public record ReviewDocumentApiRequest(String decision, String comment) {}
