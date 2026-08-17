package com.brika.platform.plan.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.plan.Plan;
import com.brika.platform.plan.PlanRepository;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §4B: "uso exclusivo SUPERADMIN" (ADR-PLATFORM-001). GLOBAL (no
 * company_id, no requireTenant()) — same pattern as ScoringRulesetController/BankController.
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

  private final AuthorizationService authorizationService;
  private final PlanRepository planRepository;

  public PlanController(AuthorizationService authorizationService, PlanRepository planRepository) {
    this.authorizationService = authorizationService;
    this.planRepository = planRepository;
  }

  @GetMapping
  public List<PlanResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "PLAN_READ");
    return planRepository.findAll().stream().map(PlanResponse::from).toList();
  }

  @PostMapping
  public PlanResponse create(
      Authentication authentication, @RequestBody CreatePlanApiRequest request) {
    authorizationService.requirePermission(authentication, "PLAN_MANAGE");
    UUID id = planRepository.insert(request.code(), request.name(), request.status());
    return PlanResponse.from(requirePlan(id));
  }

  @GetMapping("/{id}")
  public PlanResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "PLAN_READ");
    return PlanResponse.from(requirePlan(id));
  }

  @PatchMapping("/{id}")
  public PlanResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdatePlanApiRequest request) {
    authorizationService.requirePermission(authentication, "PLAN_MANAGE");
    requirePlan(id);
    planRepository.update(id, request.name(), request.status());
    return PlanResponse.from(requirePlan(id));
  }

  private Plan requirePlan(UUID id) {
    return planRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("PLAN_NOT_FOUND", "Plan not found."));
  }
}
