package com.brika.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brika.platform.tenant.TenantContext;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Impact tests approved alongside ADR-IDENTITY-001: SUPERADMIN with company_id = NULL,
 * MANAGER/BROKER/CLIENT rejected without a company, TenantContext resolving "no tenant" for
 * SUPERADMIN, and the uq_users_email_no_company partial index behaving as intended.
 */
@Testcontainers
@SpringBootTest
class UserProvisioningIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private DataSource dataSource;

  private UUID insertCompany(String taxId) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    return jdbc.queryForObject(
        "INSERT INTO companies (legal_name, trade_name, tax_id, status) VALUES (?, ?, ?,"
            + " 'ACTIVE') RETURNING id",
        UUID.class,
        "Test Co " + taxId,
        "Test Co " + taxId,
        taxId);
  }

  @Test
  void superadminCanBeCreatedWithNullCompanyId() {
    User superadmin =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.SUPERADMIN,
                null,
                "ext-" + UUID.randomUUID(),
                "super1@brika.test",
                "Super",
                "Admin"));

    assertThat(superadmin.companyId()).isNull();
  }

  @ParameterizedTest
  @EnumSource(
      value = UserRole.class,
      names = {"MANAGER", "BROKER", "CLIENT"})
  void nonSuperadminRolesAreRejectedWithoutCompanyId(UserRole role) {
    CreateUserCommand command =
        new CreateUserCommand(
            role,
            null,
            "ext-" + UUID.randomUUID(),
            role.name().toLowerCase() + "-nocompany@brika.test",
            "Test",
            "User");

    assertThatThrownBy(() -> userProvisioningService.createUser(command))
        .isInstanceOf(InvalidUserCompanyAssignmentException.class);
  }

  @Test
  void tenantContextResolvesNoTenantForSuperadminAndOwnCompanyForOtherRoles() {
    UUID companyId = insertCompany("TC-TENANT");
    User superadmin =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.SUPERADMIN,
                null,
                "ext-" + UUID.randomUUID(),
                "super2@brika.test",
                "Super",
                "Admin"));
    User manager =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.MANAGER,
                companyId,
                "ext-" + UUID.randomUUID(),
                "manager1@brika.test",
                "Man",
                "Ager"));

    assertThat(TenantContext.resolve(superadmin.role(), superadmin.companyId())).isEmpty();
    assertThat(TenantContext.resolve(manager.role(), manager.companyId())).contains(companyId);
  }

  @Test
  void twoSuperadminsCannotShareEmail() {
    userProvisioningService.createUser(
        new CreateUserCommand(
            UserRole.SUPERADMIN,
            null,
            "ext-" + UUID.randomUUID(),
            "dup-superadmin@brika.test",
            "First",
            "Superadmin"));

    CreateUserCommand secondSuperadmin =
        new CreateUserCommand(
            UserRole.SUPERADMIN,
            null,
            "ext-" + UUID.randomUUID(),
            "dup-superadmin@brika.test",
            "Second",
            "Superadmin");

    assertThatThrownBy(() -> userProvisioningService.createUser(secondSuperadmin))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void superadminAndCompanyScopedUserMayShareEmail() {
    UUID companyId = insertCompany("TC-SHARE");
    userProvisioningService.createUser(
        new CreateUserCommand(
            UserRole.MANAGER,
            companyId,
            "ext-" + UUID.randomUUID(),
            "shared@brika.test",
            "Company",
            "User"));

    User superadmin =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.SUPERADMIN,
                null,
                "ext-" + UUID.randomUUID(),
                "shared@brika.test",
                "Super",
                "Admin"));

    assertThat(superadmin.email()).isEqualTo("shared@brika.test");
  }
}
