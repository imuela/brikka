package com.brika.platform;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 1 smoke test: the application context must boot against a real, empty PostgreSQL instance
 * and Flyway must run every migration (V1-V16) successfully. This is the same physical schema
 * described in 16_POSTGRESQL_SCHEMA_SPECIFICATION.md — no JPA entities exist yet, so this test
 * asserts against raw JDBC metadata rather than a domain model. V8 (ADR-IDENTITY-001) makes
 * users.company_id nullable; V9 (ADR-RBAC-001) seeds role_permissions; V10 (Sprint 4) adds
 * document_versions.review_comment; V11 (ADR-PORTAL-AUTH-001, Sprint 7) adds the
 * CLIENT_PORTAL_ACCOUNT_CREATE permission for MANAGER/BROKER; V12 (Sprint 7 decision D3) makes
 * document_versions.uploaded_by nullable and adds uploaded_by_client_id; V13 (ADR-BANKENGINE-001,
 * Sprint 6B) adds bank_match_results/bank_match_rule_results and the BANK_MATCHING_RUN/READ
 * permissions; V14 (ADR-BANKENGINE-002, Sprint 6C) adds bank_match_rule_overrides and the
 * BANK_MATCHING_OVERRIDE permission for MANAGER/SUPERADMIN; V15 (Sprint 10, D10-1) grants the
 * already-cataloged AI_USE/AI_DOCUMENT_ANALYZE/AI_SUMMARIZE/AI_DRAFT_MESSAGE permissions to
 * SUPERADMIN/MANAGER/BROKER — no new permission code, only new role_permissions rows. V8-V12 add no
 * table; V13 adds two; V14 adds one; V15 adds none. The exact role_permissions content is verified
 * in RbacSeedIT. V16 (Sprint 20, ADR-PROCESS-008) is a data-only correction (no schema change): the
 * only cases.operation_type value in real use, "MORTGAGE", is updated to "PURCHASE" to be valid
 * under the new frontend-only OPERATION_TYPES catalog approved in that sprint. V17 (Sprint 22
 * authorization, self-auth foundations) adds 7 tables for local credentials/refresh tokens/
 * password reset tokens/login attempts — no change to any existing table.
 */
@Testcontainers
@SpringBootTest
class FlywayMigrationIT {

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

  @Test
  void contextLoadsAndFlywayMigratesEveryTable() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    Integer appliedMigrations =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);
    // 19 through V19 + V20 (Sprint 29: widen case_status_history.reason) + V21 (Sprint 29:
    // SUPERADMIN x NOTIFICATION_READ) + V22 (Sprint 30: client financial profile) = 22.
    // + V23 (Sprint 31: case_financial_analysis_results) + V24 (Sprint 31: FINANCIAL_ANALYSIS_RUN/
    // READ permissions) = 24. + V25 (Sprint 32: case_fees/case_fee_history) + V26 (Sprint 32:
    // ENGAGEMENT_CONTRACT/VIABILITY_DOSSIER document types) = 26. + V27 (BRIKKA V2 I1: document
    // checklist — documents.client_id, document_requirements unique constraint + 9 PURCHASE
    // requirement rows, no new table) = 27. + V28 (BRIKKA V2 I3: CASE_TRANSITION_OVERRIDE
    // permission + 2 role_permissions rows, no new table) = 28.
    assertThat(appliedMigrations).isEqualTo(28);

    Integer tableCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name != 'flyway_schema_history'",
            Integer.class);
    // 38 tables from V1 + 4 (V4) + 1 (V5) + 3 (V6) + 2 (V7) = 48. V8-V12 add no table.
    // V13 adds bank_match_results + bank_match_rule_results = 50. V14 adds
    // bank_match_rule_overrides = 51. V15/V16 add no table. V17 adds 7 (user_credentials,
    // portal_account_credentials, user_refresh_tokens, portal_refresh_tokens,
    // user_password_reset_tokens, portal_password_reset_tokens, login_attempts) = 58. V18-V21 add
    // no table. V22 (Sprint 30) adds client_financial_profiles + client_financial_profile_history
    // = 60. V23 (Sprint 31) adds case_financial_analysis_results = 61. V24 adds no table. V25
    // (Sprint 32) adds case_fees + case_fee_history = 63. V26 adds no table (seeds document_types
    // rows only).
    assertThat(tableCount).isEqualTo(63);

    Boolean companyIdNullable =
        jdbc.queryForObject(
            "SELECT is_nullable = 'YES' FROM information_schema.columns"
                + " WHERE table_name = 'users' AND column_name = 'company_id'",
            Boolean.class);
    assertThat(companyIdNullable).isTrue();

    Integer partialIndexCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_users_email_no_company'",
            Integer.class);
    assertThat(partialIndexCount).isEqualTo(1);

    Integer roleCount = jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
    assertThat(roleCount).isEqualTo(4);

    Integer permissionCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM permissions", Integer.class);
    assertThat(permissionCount)
        .isEqualTo(117); // 110 full atomic catalog (14_DEFINITIVE_PERMISSION_CATALOG.md) + 1
    // CLIENT_PORTAL_ACCOUNT_CREATE (ADR-PORTAL-AUTH-001, V11) + 2 BANK_MATCHING_RUN/READ
    // (ADR-BANKENGINE-001, V13) + 1 BANK_MATCHING_OVERRIDE (ADR-BANKENGINE-002, V14) + 2
    // FINANCIAL_ANALYSIS_RUN/READ (Sprint 31, V24) + 1 CASE_TRANSITION_OVERRIDE (BRIKKA V2 I3, V28)

    Integer documentTypeCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM document_types", Integer.class);
    // 10 from V2 + 2 (Sprint 32, V26: ENGAGEMENT_CONTRACT/VIABILITY_DOSSIER) = 12.
    assertThat(documentTypeCount).isEqualTo(12);

    Integer rolePermissionCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class);
    // 221 APPROVED combinations from ADR-RBAC-001 (81+71+58+11) + 2 from ADR-PORTAL-AUTH-001
    // (MANAGER/BROKER x CLIENT_PORTAL_ACCOUNT_CREATE, V11) + 6 from ADR-BANKENGINE-001
    // (SUPERADMIN/MANAGER/BROKER x BANK_MATCHING_RUN/READ, V13) + 2 from ADR-BANKENGINE-002
    // (MANAGER/SUPERADMIN x BANK_MATCHING_OVERRIDE, V14) = 231 + 12 from Sprint 10 D10-1
    // (SUPERADMIN/MANAGER/BROKER x AI_USE/AI_DOCUMENT_ANALYZE/AI_SUMMARIZE/AI_DRAFT_MESSAGE,
    // V15) = 243, + 1 from Sprint 29 stabilization (SUPERADMIN x NOTIFICATION_READ, V21) = 244,
    // + 6 from Sprint 31 (SUPERADMIN/MANAGER/BROKER x FINANCIAL_ANALYSIS_RUN/READ, V24) = 250.
    // + 2 from BRIKKA V2 I3 (MANAGER/SUPERADMIN x CASE_TRANSITION_OVERRIDE, V28) = 252.
    // Full breakdown and PENDING/NOT_ASSIGNED absence verified in RbacSeedIT.
    assertThat(rolePermissionCount).isEqualTo(252);

    Boolean reviewCommentExists =
        jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM information_schema.columns WHERE table_name ="
                + " 'document_versions' AND column_name = 'review_comment'",
            Boolean.class);
    assertThat(reviewCommentExists).isTrue();

    Boolean uploadedByNullable =
        jdbc.queryForObject(
            "SELECT is_nullable = 'YES' FROM information_schema.columns WHERE table_name ="
                + " 'document_versions' AND column_name = 'uploaded_by'",
            Boolean.class);
    assertThat(uploadedByNullable).isTrue();

    Boolean uploadedByClientIdExists =
        jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM information_schema.columns WHERE table_name ="
                + " 'document_versions' AND column_name = 'uploaded_by_client_id'",
            Boolean.class);
    assertThat(uploadedByClientIdExists).isTrue();

    Boolean bankMatchResultsExists =
        jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM information_schema.tables WHERE table_name ="
                + " 'bank_match_results'",
            Boolean.class);
    assertThat(bankMatchResultsExists).isTrue();

    Boolean bankMatchRuleResultsExists =
        jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM information_schema.tables WHERE table_name ="
                + " 'bank_match_rule_results'",
            Boolean.class);
    assertThat(bankMatchRuleResultsExists).isTrue();

    Boolean bankMatchRuleOverridesExists =
        jdbc.queryForObject(
            "SELECT COUNT(*) > 0 FROM information_schema.tables WHERE table_name ="
                + " 'bank_match_rule_overrides'",
            Boolean.class);
    assertThat(bankMatchRuleOverridesExists).isTrue();

    for (String table :
        new String[] {
          "user_credentials",
          "portal_account_credentials",
          "user_refresh_tokens",
          "portal_refresh_tokens",
          "user_password_reset_tokens",
          "portal_password_reset_tokens",
          "login_attempts"
        }) {
      Boolean exists =
          jdbc.queryForObject(
              "SELECT COUNT(*) > 0 FROM information_schema.tables WHERE table_name = ?",
              Boolean.class,
              table);
      assertThat(exists).as("table %s must exist (V17)", table).isTrue();
    }

    // V27 (BRIKKA V2 I1): documents.client_id nullable + document_requirements seed for PURCHASE.
    Boolean documentsClientIdNullable =
        jdbc.queryForObject(
            "SELECT is_nullable = 'YES' FROM information_schema.columns WHERE table_name ="
                + " 'documents' AND column_name = 'client_id'",
            Boolean.class);
    assertThat(documentsClientIdNullable).isTrue();

    Integer purchaseRequirementCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_requirements WHERE operation_type = 'PURCHASE'",
            Integer.class);
    assertThat(purchaseRequirementCount).isEqualTo(9);

    Integer mandatoryPurchaseRequirementCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM document_requirements WHERE operation_type = 'PURCHASE'"
                + " AND mandatory = true",
            Integer.class);
    assertThat(mandatoryPurchaseRequirementCount).isEqualTo(5);

    // V28 (BRIKKA V2 I3): CASE_TRANSITION_OVERRIDE granted to MANAGER + SUPERADMIN only.
    Integer caseTransitionOverrideGrants =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_permissions rp JOIN roles r ON r.id = rp.role_id JOIN"
                + " permissions p ON p.id = rp.permission_id WHERE p.code ="
                + " 'CASE_TRANSITION_OVERRIDE'",
            Integer.class);
    assertThat(caseTransitionOverrideGrants).isEqualTo(2);
  }
}
