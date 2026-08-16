package com.brika.platform.casemgmt;

import com.brika.platform.identity.User;
import java.util.UUID;

public record CaseAccessResult(User user, UUID tenantId, Case theCase) {}
