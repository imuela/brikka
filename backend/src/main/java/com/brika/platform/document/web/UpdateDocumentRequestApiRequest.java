package com.brika.platform.document.web;

/** status must be one of: PENDING, FULFILLED, CANCELLED. */
public record UpdateDocumentRequestApiRequest(String status) {}
