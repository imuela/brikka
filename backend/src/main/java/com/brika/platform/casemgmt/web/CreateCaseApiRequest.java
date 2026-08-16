package com.brika.platform.casemgmt.web;

/** operationType is free text: no catalog is documented anywhere (Sprint 3 pre-flight review). */
public record CreateCaseApiRequest(String operationType) {}
