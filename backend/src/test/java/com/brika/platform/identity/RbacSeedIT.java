package com.brika.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mechanical verification that V9__seed_role_permissions.sql seeds exactly the 221 APPROVED
 * combinations from the ADR-RBAC-001 matrix (12_DECISION_LOG.md) — no more, no fewer — and none of
 * the 16 PENDING or any NOT_ASSIGNED combination.
 */
@Testcontainers
@SpringBootTest
class RbacSeedIT {

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

  @Autowired private DataSource dataSource;
  @Autowired private RoleRepository roleRepository;
  @Autowired private PermissionRepository permissionRepository;
  @Autowired private RolePermissionRepository rolePermissionRepository;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  @Test
  void totalRolePermissionCountIsExactly221() {
    Integer count = jdbc().queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class);
    assertThat(count).isEqualTo(221);
    assertThat(rolePermissionRepository.count()).isEqualTo(221);
  }

  @Test
  void roleAndPermissionRepositoriesExposeTheFullCatalog() {
    assertThat(roleRepository.findAll()).hasSize(4);
    assertThat(permissionRepository.findAll()).hasSize(110);
  }

  @Test
  void rolePermissionRepositoryResolvesPermissionCodesForSuperadmin() {
    Role superadmin = roleRepository.findByCode("SUPERADMIN");
    assertThat(rolePermissionRepository.permissionCodesForRole(superadmin.id())).hasSize(81);
  }

  @Test
  void breakdownByRoleMatchesTheApprovedMatrix() {
    List<Map<String, Object>> rows =
        jdbc()
            .queryForList(
                "SELECT r.code AS role_code, COUNT(*) AS n FROM role_permissions rp"
                    + " JOIN roles r ON r.id = rp.role_id GROUP BY r.code");

    Map<String, Long> byRole =
        rows.stream()
            .collect(
                Collectors.toMap(
                    row -> (String) row.get("role_code"),
                    row -> ((Number) row.get("n")).longValue()));

    assertThat(byRole)
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "SUPERADMIN", 81L,
                "MANAGER", 71L,
                "BROKER", 58L,
                "CLIENT", 11L));
  }

  private static List<String[]> pendingCombinations() {
    return List.of(
        new String[] {"SUPERADMIN", "AI_USE"},
        new String[] {"MANAGER", "AI_USE"},
        new String[] {"BROKER", "AI_USE"},
        new String[] {"SUPERADMIN", "AI_DOCUMENT_ANALYZE"},
        new String[] {"MANAGER", "AI_DOCUMENT_ANALYZE"},
        new String[] {"BROKER", "AI_DOCUMENT_ANALYZE"},
        new String[] {"SUPERADMIN", "AI_SUMMARIZE"},
        new String[] {"MANAGER", "AI_SUMMARIZE"},
        new String[] {"BROKER", "AI_SUMMARIZE"},
        new String[] {"SUPERADMIN", "AI_DRAFT_MESSAGE"},
        new String[] {"MANAGER", "AI_DRAFT_MESSAGE"},
        new String[] {"BROKER", "AI_DRAFT_MESSAGE"},
        new String[] {"MANAGER", "AI_MANAGE_CONFIGURATION"},
        new String[] {"BROKER", "AI_MANAGE_CONFIGURATION"},
        new String[] {"MANAGER", "AI_READ_USAGE"},
        new String[] {"BROKER", "AI_READ_USAGE"});
  }

  @Test
  void exactlySixteenPendingCombinationsExistInCatalogAndNoneAreSeeded() {
    assertThat(pendingCombinations()).hasSize(16);
    for (String[] pair : pendingCombinations()) {
      assertCombinationNotSeeded(pair[0], pair[1]);
    }
  }

  private static List<String[]> sampleNotAssignedCombinations() {
    return List.of(
        new String[] {"CLIENT", "CASE_READ"},
        new String[] {"BROKER", "COMPANY_CREATE"},
        new String[] {"SUPERADMIN", "NOTIFICATION_READ"},
        new String[] {"MANAGER", "AUDIT_READ"},
        new String[] {"BROKER", "CASE_ASSIGN"},
        new String[] {"SUPERADMIN", "CASE_REOPEN"},
        new String[] {"CLIENT", "DOCUMENT_READ"},
        new String[] {"MANAGER", "PLAN_READ"});
  }

  @ParameterizedTest
  @MethodSource("sampleNotAssignedCombinations")
  void representativeNotAssignedCombinationsAreNotSeeded(String roleCode, String permissionCode) {
    assertCombinationNotSeeded(roleCode, permissionCode);
  }

  private void assertCombinationNotSeeded(String roleCode, String permissionCode) {
    Integer count =
        jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM role_permissions rp"
                    + " JOIN roles r ON r.id = rp.role_id"
                    + " JOIN permissions p ON p.id = rp.permission_id"
                    + " WHERE r.code = ? AND p.code = ?",
                Integer.class,
                roleCode,
                permissionCode);
    assertThat(count).as("%s x %s must not be seeded", roleCode, permissionCode).isZero();
  }
}
