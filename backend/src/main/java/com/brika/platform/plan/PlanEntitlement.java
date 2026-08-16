package com.brika.platform.plan;

import java.util.UUID;

/** value is raw JSON text (plan_entitlements.value is jsonb, e.g. "true", "50", "{...}"). */
public record PlanEntitlement(UUID planId, UUID entitlementId, String value) {}
