package com.brika.platform.casemgmt;

import java.util.UUID;

public record CaseClient(
    UUID caseId, UUID clientId, ParticipationType participationType, boolean isPrimary) {}
