package com.brika.platform.identity;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal user creation slice needed to support ADR-IDENTITY-001: SUPERADMIN may have no company,
 * MANAGER/BROKER/CLIENT always must. Authentication, TenantContext wiring against a real principal,
 * and the rest of Identity/RBAC (Sprint 2) are out of scope here.
 */
@Service
public class UserProvisioningService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;

  public UserProvisioningService(UserRepository userRepository, RoleRepository roleRepository) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
  }

  @Transactional
  public User createUser(CreateUserCommand command) {
    validateCompanyAssignment(command.role(), command.companyId());

    UUID userId =
        userRepository.insertUser(
            command.companyId(),
            command.externalIdentityId(),
            command.email(),
            command.firstName(),
            command.lastName());
    Role role = roleRepository.findByCode(command.role().name());
    userRepository.insertUserRole(userId, role.id());

    return new User(
        userId,
        command.companyId(),
        command.email(),
        command.firstName(),
        command.lastName(),
        "ACTIVE",
        command.role());
  }

  private void validateCompanyAssignment(UserRole role, UUID companyId) {
    if (role == UserRole.SUPERADMIN) {
      if (companyId != null) {
        throw new InvalidUserCompanyAssignmentException(
            "SUPERADMIN must not have a company_id (ADR-IDENTITY-001).");
      }
      return;
    }
    if (companyId == null) {
      throw new InvalidUserCompanyAssignmentException(
          role + " requires a company_id (ADR-IDENTITY-001).");
    }
  }
}
