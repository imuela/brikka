package com.brika.platform.portal;

import com.brika.platform.casemgmt.Case;
import com.brika.platform.crm.ClientPortalAccount;
import java.util.UUID;

public record PortalCaseAccessResult(ClientPortalAccount account, UUID tenantId, Case theCase) {}
