package com.brika.platform.plan;

import java.util.UUID;

public record CompanySubscription(UUID id, UUID companyId, UUID planId, String status) {}
