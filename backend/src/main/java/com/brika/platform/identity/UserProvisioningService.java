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

  public UserProvisioningService(UserRepository userRepository) {
    this.userRepository = userRepository;
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
    UUID roleId = userRepository.findRoleIdByCode(command.role().name());
    userRepository.insertUserRole(userId, roleId);

    return new User(userId, command.companyId(), command.email(), command.role());
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
