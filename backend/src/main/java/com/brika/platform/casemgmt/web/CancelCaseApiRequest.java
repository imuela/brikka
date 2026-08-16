package com.brika.platform.casemgmt.web;

/** reason must be one of the catalog values in CancellationReason. */
public record CancelCaseApiRequest(String reason, String comment) {}
