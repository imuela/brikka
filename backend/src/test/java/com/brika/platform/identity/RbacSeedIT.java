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
 * the 16 PENDING or any NOT_ASSIGNED combination. V11__portal_account_permission.sql
 * (ADR-PORTAL-AUTH-001, Sprint 7) adds 2 further combinations (MANAGER/BROKER x
 * CLIENT_PORTAL_ACCOUNT_CREATE); V13__bank_matching_engine.sql (ADR-BANKENGINE-001, Sprint 6B) adds
 * 6 more (SUPERADMIN/MANAGER/BROKER x BANK_MATCHING_RUN/READ); V14__bank_matching_overrides.sql
 * (ADR-BANKENGINE-002, Sprint 6C) adds 2 more (MANAGER/SUPERADMIN x BANK_MATCHING_OVERRIDE,
 * deliberately excluding BROKER and CLIENT); V15__ai_use_permissions.sql (Sprint 10, D10-1) adds 12
 * more (SUPERADMIN/MANAGER/BROKER x AI_USE/AI_DOCUMENT_ANALYZE/AI_SUMMARIZE/AI_DRAFT_MESSAGE,
 * deliberately excluding CLIENT) — counts below reflect the full schema state after every
 * migration, i.e. 221 + 2 + 6 + 2 + 12 = 243. Of the original 16 PENDING combinations, only the 4
 * AI_MANAGE_CONFIGURATION/AI_READ_USAGE x MANAGER/BROKER combinations remain PENDING (still not
 * seeded, out of Sprint 10 scope).
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
  void totalRolePermissionCountIsExactly250() {
    // 243 through V15, + 1 (Sprint 29 stabilization, V21: SUPERADMIN x NOTIFICATION_READ),
    // + 6 (Sprint 31, V24: FINANCIAL_ANALYSIS_RUN/READ x SUPERADMIN/MANAGER/BROKER).
    Integer count = jdbc().queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class);
    assertThat(count).isEqualTo(250);
    assertThat(rolePermissionRepository.count()).isEqualTo(250);
  }

  @Test
  void roleAndPermissionRepositoriesExposeTheFullCatalog() {
    assertThat(roleRepository.findAll()).hasSize(4);
    // 114 through V21, + 2 (Sprint 31, V24: FINANCIAL_ANALYSIS_RUN/READ).
    assertThat(permissionRepository.findAll()).hasSize(116);
  }

  @Test
  void rolePermissionRepositoryResolvesPermissionCodesForSuperadmin() {
    Role superadmin = roleRepository.findByCode("SUPERADMIN");
    // 89 through V21, + 2 (Sprint 31, V24: FINANCIAL_ANALYSIS_RUN/READ).
    assertThat(rolePermissionRepository.permissionCodesForRole(superadmin.id())).hasSize(91);
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
                "SUPERADMIN",
                    91L, // 81 (ADR-RBAC-001) + 2 (ADR-BANKENGINE-001, V13) + 1 (ADR-BANKENGINE-002,
                // V14) + 4 (Sprint 10 D10-1, V15: AI_USE/AI_DOCUMENT_ANALYZE/AI_SUMMARIZE/
                // AI_DRAFT_MESSAGE) + 1 (Sprint 29 stabilization, V21: NOTIFICATION_READ — an
                // oversight from before Sprint 27's GLOBAL model; notifications stay scoped to the
                // caller's own recipientUserId regardless of this grant) + 2 (Sprint 31, V24:
                // FINANCIAL_ANALYSIS_RUN/READ)
                "MANAGER", 81L, // 71 (ADR-RBAC-001) + 1 (V11) + 2 (ADR-BANKENGINE-001, V13) + 1
                // (ADR-BANKENGINE-002, V14) + 4 (Sprint 10 D10-1, V15) + 2 (Sprint 31, V24:
                // FINANCIAL_ANALYSIS_RUN/READ)
                "BROKER", 67L, // 58 (ADR-RBAC-001) + 1 (V11) + 2 (ADR-BANKENGINE-001, V13) + 4
                // (Sprint 10 D10-1, V15) + 2 (Sprint 31, V24: FINANCIAL_ANALYSIS_RUN/READ)
                "CLIENT", 11L)); // unchanged — Sprint 31 deliberately never grants CLIENT/Portal
    // access to financial analysis (V24 seeds no CLIENT rows)
  }

  /**
   * Sprint 10 D10-1 (V15) grants SUPERADMIN/MANAGER/BROKER x AI_USE/AI_DOCUMENT_ANALYZE/
   * AI_SUMMARIZE/AI_DRAFT_MESSAGE — these 12 combinations moved out of PENDING into seeded. Only
   * AI_MANAGE_CONFIGURATION/AI_READ_USAGE x MANAGER/BROKER remain PENDING (out of Sprint 10 scope,
   * SUPERADMIN already had both since V9).
   */
  private static List<String[]> nowGrantedByV15() {
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
        new String[] {"BROKER", "AI_DRAFT_MESSAGE"});
  }

  private static List<String[]> pendingCombinations() {
    return List.of(
        new String[] {"MANAGER", "AI_MANAGE_CONFIGURATION"},
        new String[] {"BROKER", "AI_MANAGE_CONFIGURATION"},
        new String[] {"MANAGER", "AI_READ_USAGE"},
        new String[] {"BROKER", "AI_READ_USAGE"});
  }

  @Test
  void exactlyFourPendingCombinationsExistInCatalogAndNoneAreSeeded() {
    assertThat(pendingCombinations()).hasSize(4);
    for (String[] pair : pendingCombinations()) {
      assertCombinationNotSeeded(pair[0], pair[1]);
    }
  }

  @ParameterizedTest
  @MethodSource("nowGrantedByV15")
  void v15AiUsePermissionsAreSeededForSuperadminManagerBroker(
      String roleCode, String permissionCode) {
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
    assertThat(count).as("%s x %s must be seeded by V15", roleCode, permissionCode).isEqualTo(1);
  }

  @ParameterizedTest
  @MethodSource("v15AiPermissionCodes")
  void clientNeverReceivesAnyAiUsePermission(String permissionCode) {
    assertCombinationNotSeeded("CLIENT", permissionCode);
  }

  private static List<String> v15AiPermissionCodes() {
    return List.of("AI_USE", "AI_DOCUMENT_ANALYZE", "AI_SUMMARIZE", "AI_DRAFT_MESSAGE");
  }

  /**
   * Sprint 29 (stabilization, V21): SUPERADMIN never had NOTIFICATION_READ, so the "Notificaciones"
   * screen 403'd on every load for the one role with the broadest operational visibility elsewhere.
   * NotificationController scopes strictly to the caller's own recipientUserId, so this grant only
   * lets SUPERADMIN read their own notifications — never anyone else's.
   */
  @Test
  void v21NotificationReadIsSeededForSuperadmin() {
    Integer count =
        jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM role_permissions rp"
                    + " JOIN roles r ON r.id = rp.role_id"
                    + " JOIN permissions p ON p.id = rp.permission_id"
                    + " WHERE r.code = 'SUPERADMIN' AND p.code = 'NOTIFICATION_READ'",
                Integer.class);
    assertThat(count).as("SUPERADMIN x NOTIFICATION_READ must be seeded by V21").isEqualTo(1);
  }

  private static List<String[]> sampleNotAssignedCombinations() {
    return List.of(
        new String[] {"CLIENT", "CASE_READ"},
        new String[] {"BROKER", "COMPANY_CREATE"},
        new String[] {"BROKER", "NOTIFICATION_MANAGE"},
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
