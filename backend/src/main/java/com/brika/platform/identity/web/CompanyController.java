package com.brika.platform.identity.web;

import com.brika.platform.common.error.ConflictException;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.identity.Company;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRole;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 12.1 (17_API_SPECIFICATION_DETAILED.md §4B / 05_API_SPECIFICATION.md §2): {@code
 * /companies} was listed as a top-level resource but never given a detailed contract — this
 * controller fills that gap. Dual scope per 12_DECISION_LOG.md's RBAC matrix: COMPANY_CREATE/
 * SUSPEND/DELETE are SUPERADMIN-only and GLOBAL (platform lifecycle, not tenant-owned data, so no
 * SUPPORT_SESSION is required); COMPANY_READ/UPDATE are additionally APPROVED (TENANT) for MANAGER,
 * scoped to their own company only. D-MASTER-2: DELETE is a logical status transition to {@code
 * DELETED} (never a physical row delete) — every {@code company_id} FK in the schema lacks ON
 * DELETE CASCADE, so a real DELETE would fail the instant any dependent row exists, and it matches
 * the project-wide soft-delete/status-transition convention (USER_DISABLE, CASE_CANCEL).
 */
@RestController
@RequestMapping("/api/v1/companies")
public class CompanyController {

  private final AuthorizationService authorizationService;
  private final CompanyRepository companyRepository;

  public CompanyController(
      AuthorizationService authorizationService, CompanyRepository companyRepository) {
    this.authorizationService = authorizationService;
    this.companyRepository = companyRepository;
  }

  @GetMapping
  public List<CompanyResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "COMPANY_READ");
    User user = authorizationService.currentUser(authentication);
    if (user.role() == UserRole.SUPERADMIN) {
      return companyRepository.findAll().stream().map(CompanyResponse::from).toList();
    }
    UUID tenantId = authorizationService.requireTenant(authentication);
    return List.of(CompanyResponse.from(requireCompany(tenantId)));
  }

  @PostMapping
  public CompanyResponse create(
      Authentication authentication, @RequestBody CreateCompanyApiRequest request) {
    authorizationService.requirePermission(authentication, "COMPANY_CREATE");
    UUID id = companyRepository.insert(request.legalName(), request.tradeName(), request.taxId());
    return CompanyResponse.from(requireCompany(id));
  }

  @GetMapping("/{id}")
  public CompanyResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "COMPANY_READ");
    requireOwnCompanyUnlessSuperadmin(authentication, id);
    return CompanyResponse.from(requireCompany(id));
  }

  @PatchMapping("/{id}")
  public CompanyResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateCompanyApiRequest request) {
    authorizationService.requirePermission(authentication, "COMPANY_UPDATE");
    requireOwnCompanyUnlessSuperadmin(authentication, id);
    requireCompany(id);
    companyRepository.update(id, request.legalName(), request.tradeName(), request.taxId());
    return CompanyResponse.from(requireCompany(id));
  }

  @PostMapping("/{id}/suspend")
  public CompanyResponse suspend(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "COMPANY_SUSPEND");
    Company company = requireCompany(id);
    if (!"ACTIVE".equals(company.status())) {
      throw new ConflictException("COMPANY_NOT_ACTIVE", "Only an ACTIVE company can be suspended.");
    }
    companyRepository.updateStatus(id, "SUSPENDED");
    return CompanyResponse.from(requireCompany(id));
  }

  @DeleteMapping("/{id}")
  public CompanyResponse delete(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "COMPANY_DELETE");
    Company company = requireCompany(id);
    if ("DELETED".equals(company.status())) {
      throw new ConflictException("COMPANY_ALREADY_DELETED", "Company is already deleted.");
    }
    companyRepository.updateStatus(id, "DELETED");
    return CompanyResponse.from(requireCompany(id));
  }

  /**
   * MANAGER may only act on their own company; a foreign id is masked as 404, like every other
   * cross-tenant lookup in this codebase. SUPERADMIN is unrestricted (GLOBAL scope).
   */
  private void requireOwnCompanyUnlessSuperadmin(Authentication authentication, UUID id) {
    User user = authorizationService.currentUser(authentication);
    if (user.role() == UserRole.SUPERADMIN) {
      return;
    }
    UUID tenantId = authorizationService.requireTenant(authentication);
    if (!tenantId.equals(id)) {
      throw new ResourceNotFoundException("COMPANY_NOT_FOUND", "Company not found.");
    }
  }

  private Company requireCompany(UUID id) {
    return companyRepository
        .findById(id)
        .orElseThrow(
            () -> new ResourceNotFoundException("COMPANY_NOT_FOUND", "Company not found."));
  }
}
