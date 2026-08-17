package com.brika.platform.plan.web;

import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.plan.CompanySubscription;
import com.brika.platform.plan.CompanySubscriptionRepository;
import com.brika.platform.plan.PlanRepository;
import com.brika.platform.security.AuthorizationService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §4B: "uso exclusivo SUPERADMIN" (ADR-PLATFORM-001). GLOBAL, no
 * requireTenant() — same as PlanController. company_subscriptions.company_id is UNIQUE (V4), so PUT
 * is a true upsert: insert on first call, update thereafter.
 */
@RestController
@RequestMapping("/api/v1/companies/{companyId}/subscription")
public class CompanySubscriptionController {

  private final AuthorizationService authorizationService;
  private final CompanyRepository companyRepository;
  private final PlanRepository planRepository;
  private final CompanySubscriptionRepository companySubscriptionRepository;

  public CompanySubscriptionController(
      AuthorizationService authorizationService,
      CompanyRepository companyRepository,
      PlanRepository planRepository,
      CompanySubscriptionRepository companySubscriptionRepository) {
    this.authorizationService = authorizationService;
    this.companyRepository = companyRepository;
    this.planRepository = planRepository;
    this.companySubscriptionRepository = companySubscriptionRepository;
  }

  @GetMapping
  public CompanySubscriptionResponse get(
      Authentication authentication, @PathVariable UUID companyId) {
    authorizationService.requirePermission(authentication, "SUBSCRIPTION_READ");
    requireCompany(companyId);
    return CompanySubscriptionResponse.from(requireSubscription(companyId));
  }

  @PutMapping
  public CompanySubscriptionResponse upsert(
      Authentication authentication,
      @PathVariable UUID companyId,
      @RequestBody UpsertCompanySubscriptionApiRequest request) {
    authorizationService.requirePermission(authentication, "SUBSCRIPTION_MANAGE");
    requireCompany(companyId);
    if (planRepository.findById(request.planId()).isEmpty()) {
      throw new ValidationException("PLAN_NOT_FOUND", "Unknown planId.");
    }
    if (companySubscriptionRepository.findByCompanyId(companyId).isPresent()) {
      companySubscriptionRepository.updatePlanAndStatus(
          companyId, request.planId(), request.status());
    } else {
      companySubscriptionRepository.insert(companyId, request.planId(), request.status());
    }
    return CompanySubscriptionResponse.from(requireSubscription(companyId));
  }

  @PostMapping("/cancel")
  public CompanySubscriptionResponse cancel(
      Authentication authentication, @PathVariable UUID companyId) {
    authorizationService.requirePermission(authentication, "SUBSCRIPTION_MANAGE");
    requireCompany(companyId);
    requireSubscription(companyId);
    companySubscriptionRepository.cancel(companyId);
    return CompanySubscriptionResponse.from(requireSubscription(companyId));
  }

  private void requireCompany(UUID id) {
    if (companyRepository.findById(id).isEmpty()) {
      throw new ResourceNotFoundException("COMPANY_NOT_FOUND", "Company not found.");
    }
  }

  private CompanySubscription requireSubscription(UUID companyId) {
    return companySubscriptionRepository
        .findByCompanyId(companyId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "SUBSCRIPTION_NOT_FOUND", "Company has no subscription."));
  }
}
