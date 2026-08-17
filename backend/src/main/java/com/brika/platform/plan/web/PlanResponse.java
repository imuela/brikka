package com.brika.platform.plan.web;

import com.brika.platform.plan.Plan;
import java.util.UUID;

public record PlanResponse(UUID id, String code, String name, String status) {

  public static PlanResponse from(Plan plan) {
    return new PlanResponse(plan.id(), plan.code(), plan.name(), plan.status());
  }
}
