package com.brika.platform.plan.web;

import java.util.UUID;

public record UpsertCompanySubscriptionApiRequest(UUID planId, String status) {}
