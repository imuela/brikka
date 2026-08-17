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
 * 17_API_SPECIFICATION_DETAILED.md §5. Scope is always the caller's own tenant, resolved server-
 * side (AuthorizationService.requireTenant) — company_id is never accepted from the client
 * (06_SECURITY_SPECIFICATION.md §4). In practice only MANAGER/BROKER can pass requireTenant: a
 * SUPERADMIN request is denied since SUPPORT_SESSION is not implemented (ADR-RBAC-001).
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
    UUID tenantId = authorizationService.requireTenant(authentication);
    return userRepository.findAllByCompanyId(tenantId).stream().map(UserResponse::from).toList();
  }

  @GetMapping("/{id}")
  public UserResponse get(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "USER_READ");
    UUID tenantId = authorizationService.requireTenant(authentication);
    return UserResponse.from(requireUserInTenant(id, tenantId));
  }

  @PostMapping
  public UserResponse create(
      Authentication authentication, @RequestBody CreateUserApiRequest request) {
    authorizationService.requirePermission(authentication, "USER_CREATE");
    UUID tenantId = authorizationService.requireTenant(authentication);
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
    UUID tenantId = authorizationService.requireTenant(authentication);
    requireUserInTenant(id, tenantId);
    userRepository.updateName(id, request.firstName(), request.lastName());
    return UserResponse.from(requireUserInTenant(id, tenantId));
  }

  @PostMapping("/{id}/disable")
  public UserResponse disable(Authentication authentication, @PathVariable UUID id) {
    authorizationService.requirePermission(authentication, "USER_DISABLE");
    UUID tenantId = authorizationService.requireTenant(authentication);
    requireUserInTenant(id, tenantId);
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

  /** A user that exists but belongs to another tenant is reported the same as "not found". */
  private User requireUserInTenant(UUID id, UUID tenantId) {
    return userRepository
        .findById(id)
        .filter(user -> tenantId.equals(user.companyId()))
        .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));
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
