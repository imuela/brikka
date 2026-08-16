package com.brika.platform.document;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.identity.User;
import java.util.UUID;

public record DocumentAccessResult(User user, UUID tenantId, Case theCase, Document document) {}
