package com.brika.platform.plan.web;

import com.brika.platform.plan.CompanySubscription;
import java.util.UUID;

public record CompanySubscriptionResponse(UUID id, UUID companyId, UUID planId, String status) {

  public static CompanySubscriptionResponse from(CompanySubscription subscription) {
    return new CompanySubscriptionResponse(
        subscription.id(), subscription.companyId(), subscription.planId(), subscription.status());
  }
}
