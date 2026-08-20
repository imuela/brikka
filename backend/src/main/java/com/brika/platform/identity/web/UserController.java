package com.brika.platform.identity.web;

import com.brika.platform.audit.AuditEventWriter;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.InvalidUserCompanyAssignmentException;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRepository;
import com.brika.platform.identity.UserRole;
import com.brika.platform.security.AuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * 17_API_SPECIFICATION_DETAILED.md §5. For a tenant user (MANAGER/BROKER) scope is always the
 * caller's own tenant, resolved server-side (AuthorizationService.requireTenant) — company_id is
 * never accepted from the client (06_SECURITY_SPECIFICATION.md §4). A GLOBAL SUPERADMIN (Sprint 27,
 * ADR-RBAC-002) reads across all companies and resolves the tenant from the target resource; user
 * creation for SUPERADMIN requires an explicit companyId (the platform admin has no company of
 * their own).
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final AuthorizationService authorizationService;
  private final UserRepository userRepository;
  private final UserProvisioningService userProvisioningService;
  private final AuditEventWriter auditEventWriter;
  private final ObjectMapper objectMapper;

  public UserController(
      AuthorizationService authorizationService,
      UserRepository userRepository,
      UserProvisioningService userProvisioningService,
      AuditEventWriter auditEventWriter,
      ObjectMapper objectMapper) {
    this.authorizationService = authorizationService;
    this.userRepository = userRepository;
    this.userProvisioningService = userProvisioningService;
    this.auditEventWriter = auditEventWriter;
    this.objectMapper = objectMapper;
  }

  @GetMapping
  public List<UserResponse> list(Authentication authentication) {
    authorizationService.requirePermission(authentication, "USER_READ");
    if (authorizationService.isSuperadmin(authentication)) {
      return userRepository.findAll().stream().map(UserResponse::from).toList();
    }
    UUID tenantId = authorizationService.requireTenant(authentication);
    return userRepository.findAllByCompanyId(tenantId).stream().map(UserResponse::from).toList();
  }

  @GetMapping("/{id}")
  public UserResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "USER_READ");
    if (authorizationService.isSuperadmin(authentication)) {
      return UserResponse.from(requireUser(id));
    }
    UUID tenantId = authorizationService.requireTenant(authentication);
    return UserResponse.from(requireUserInTenant(id, tenantId));
  }

  @PostMapping
  public UserResponse create(
      Authentication authentication, @RequestBody CreateUserApiRequest request) {
    authorizationService.requirePermission(authentication, "USER_CREATE");
    UUID tenantId;
    if (authorizationService.isSuperadmin(authentication)) {
      tenantId = requireNotNull(request.companyId(), "SUPERADMIN user creation requires companyId");
    } else {
      tenantId = authorizationService.requireTenant(authentication);
    }
    UserRole role = parseRole(request.role());
    try {
      User created =
          userProvisioningService.createUser(
              new CreateUserCommand(
                  role,
                  tenantId,
                  request.externalIdentityId(),
                  request.email(),
                  request.firstName(),
                  request.lastName()));
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("role", role.name());
      metadata.put("email", request.email());
      auditEventWriter.write(
          tenantId,
          authorizationService.currentUser(authentication).id(),
          null,
          "USER_CREATED",
          "USER",
          created.id(),
          toJson(metadata));
      return UserResponse.from(created);
    } catch (InvalidUserCompanyAssignmentException e) {
      throw new ValidationException("INVALID_ROLE_ASSIGNMENT", e.getMessage());
    }
  }

  @PatchMapping("/{id}")
  public UserResponse update(
      Authentication authentication,
      @PathVariable UUID id,
      @RequestBody UpdateUserApiRequest request) {
    authorizationService.requirePermission(authentication, "USER_UPDATE");
    UUID tenantId = resolveTenantForTargetUser(authentication, id);
    userRepository.updateName(id, request.firstName(), request.lastName());
    return UserResponse.from(requireUserInTenant(id, tenantId));
  }

  @PostMapping("/{id}/disable")
  public UserResponse disable(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "USER_DISABLE");
    UUID tenantId = resolveTenantForTargetUser(authentication, id);
    userRepository.disable(id);
    auditEventWriter.write(
        tenantId,
        authorizationService.currentUser(authentication).id(),
        null,
        "USER_DISABLED",
        "USER",
        id,
        toJson(Map.of("userId", id.toString())));
    return UserResponse.from(requireUserInTenant(id, tenantId));
  }

  /**
   * GLOBAL SUPERADMIN (ADR-RBAC-002) resolves the tenant from the target user's company; tenant
   * users resolve it from their own active session. Either way the target must exist and, for a
   * tenant user, must belong to the caller's tenant.
   */
  private UUID resolveTenantForTargetUser(Authentication authentication, UUID id) {
    if (authorizationService.isSuperadmin(authentication)) {
      return requireUser(id).companyId();
    }
    UUID tenantId = authorizationService.requireTenant(authentication);
    requireUserInTenant(id, tenantId);
    return tenantId;
  }

  /** A user that exists but belongs to another tenant is reported the same as "not found". */
  private User requireUserInTenant(UUID id, UUID tenantId) {
    return userRepository
        .findById(id)
        .filter(user -> tenantId.equals(user.companyId()))
        .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));
  }

  private User requireUser(UUID id) {
    return userRepository
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));
  }

  private UUID requireNotNull(UUID value, String message) {
    if (value == null) {
      throw new ValidationException("MISSING_COMPANY_ID", message);
    }
    return value;
  }

  private UserRole parseRole(String role) {
    try {
      return UserRole.valueOf(role);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("INVALID_ROLE", "Unknown role: " + role);
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize audit metadata", e);
    }
  }
}
