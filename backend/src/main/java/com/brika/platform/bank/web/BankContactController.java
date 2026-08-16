package com.brika.platform.bank.web;

import com.brika.platform.bank.BankContact;
import com.brika.platform.bank.BankContactRepository;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRole;
import com.brika.platform.security.AuthorizationService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 17_API_SPECIFICATION_DETAILED.md §13, 06_BANK_ENGINE_SPECIFICATION.md §3-4, ADR-BANK-001:
 * BANK_CONTACT is tenant-owned (TENANT scope, no CASE ASSIGNMENT — bank_contacts has no case_id).
 * PRIVATE visibility (Sprint 5 pre-flight decision): visible to its owner and to MANAGER; a BROKER
 * other than the owner is denied as if the contact did not exist (404), matching the cross-tenant
 * masking convention used throughout the platform.
 */
@RestController
@RequestMapping("/api/v1/bank-contacts")
public class BankContactController {

  private static final Set<String> VALID_VISIBILITIES = Set.of("COMPANY", "PRIVATE");

  private final AuthorizationService authorizationService;
  private final BankContactRepository bankContactRepository;

  public BankContactController(
      AuthorizationService authorizationService, BankContactRepository bankContactRepository) {
    this.authorizationService = authorizationService;
    this.bankContactRepository = bankContactRepository;
  }

  @GetMapping
  public List<BankContactResponse> list(
      Authentication authentication, @RequestParam(required = false) UUID bankId) {
    authorizationService.requirePermission(authentication, "BANK_CONTACT_READ");
    UUID tenantId = authorizationService.requireTenant(authentication);
    User user = authorizationService.currentUser(authentication);
    List<BankContact> contacts =
        bankId == null
            ? bankContactRepository.findAllByCompanyId(tenantId)
            : bankContactRepository.findAllByCompanyIdAndBankId(tenantId, bankId);
    return contacts.stream()
        .filter(contact -> visible(contact, user))
        .map(BankContactResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  public BankContactResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "BANK_CONTACT_READ");
    UUID tenantId = authorizationService.requireTenant(authentication);
    User user = authorizationService.currentUser(authentication);
    return BankContactResponse.from(requireVisibleContact(id, tenantId, user));
  }

  @PostMapping
  public BankContactResponse create(
      Authentication authentication, @RequestBody CreateBankContactApiRequest request) {
    authorizationService.requirePermission(authentication, "BANK_CONTACT_CREATE");
    UUID tenantId = authorizationService.requireTenant(authentication);
    User user = authorizationService.currentUser(authentication);
    String visibility = requireValidVisibility(request.visibility());
    UUID id =
        bankContactRepository.insert(
            tenantId,
            request.bankId(),
            user.id(),
            request.name(),
            request.position(),
            request.department(),
            request.branch(),
            request.email(),
            request.phone(),
            request.secondaryPhone(),
            request.notes(),
            visibility);
    return BankContactResponse.from(bankContactRepository.findById(id).orElseThrow());
  }

  @PatchMapping("/{id}")
  public BankContactResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateBankContactApiRequest request) {
    authorizationService.requirePermission(authentication, "BANK_CONTACT_UPDATE");
    UUID tenantId = authorizationService.requireTenant(authentication);
    User user = authorizationService.currentUser(authentication);
    requireVisibleContact(id, tenantId, user);
    String visibility = requireValidVisibility(request.visibility());
    bankContactRepository.update(
        id,
        request.name(),
        request.position(),
        request.department(),
        request.branch(),
        request.email(),
        request.phone(),
        request.secondaryPhone(),
        request.notes(),
        visibility,
        request.active());
    return BankContactResponse.from(bankContactRepository.findById(id).orElseThrow());
  }

  @DeleteMapping("/{id}")
  public void delete(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "BANK_CONTACT_DELETE");
    UUID tenantId = authorizationService.requireTenant(authentication);
    User user = authorizationService.currentUser(authentication);
    requireVisibleContact(id, tenantId, user);
    bankContactRepository.delete(id);
  }

  private String requireValidVisibility(String visibility) {
    if (!VALID_VISIBILITIES.contains(visibility)) {
      throw new ValidationException(
          "INVALID_VISIBILITY", "visibility must be one of " + VALID_VISIBILITIES);
    }
    return visibility;
  }

  private boolean visible(BankContact contact, User user) {
    if (user.role() == UserRole.MANAGER) {
      return true;
    }
    return "COMPANY".equals(contact.visibility()) || user.id().equals(contact.ownerUserId());
  }

  /**
   * A contact in another tenant, or a PRIVATE contact not owned by this user, is reported as not
   * found.
   */
  private BankContact requireVisibleContact(UUID id, UUID tenantId, User user) {
    return bankContactRepository
        .findById(id)
        .filter(contact -> tenantId.equals(contact.companyId()))
        .filter(contact -> visible(contact, user))
        .orElseThrow(
            () ->
                new ResourceNotFoundException("BANK_CONTACT_NOT_FOUND", "Bank contact not found."));
  }
}
