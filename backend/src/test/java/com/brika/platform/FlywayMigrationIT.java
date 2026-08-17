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
 * and Flyway must run every migration (V1-V14) successfully. This is the same physical schema
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
 * in RbacSeedIT.
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
    assertThat(appliedMigrations).isEqualTo(15);

    Integer tableCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name != 'flyway_schema_history'",
            Integer.class);
    // 38 tables from V1 + 4 (V4) + 1 (V5) + 3 (V6) + 2 (V7) = 48. V8-V12 add no table.
    // V13 adds bank_match_results + bank_match_rule_results = 50. V14 adds
    // bank_match_rule_overrides = 51.
    assertThat(tableCount).isEqualTo(51);

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
        .isEqualTo(114); // 110 full atomic catalog (14_DEFINITIVE_PERMISSION_CATALOG.md) + 1
    // CLIENT_PORTAL_ACCOUNT_CREATE (ADR-PORTAL-AUTH-001, V11) + 2 BANK_MATCHING_RUN/READ
    // (ADR-BANKENGINE-001, V13) + 1 BANK_MATCHING_OVERRIDE (ADR-BANKENGINE-002, V14)

    Integer documentTypeCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM document_types", Integer.class);
    assertThat(documentTypeCount).isEqualTo(10);

    Integer rolePermissionCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class);
    // 221 APPROVED combinations from ADR-RBAC-001 (81+71+58+11) + 2 from ADR-PORTAL-AUTH-001
    // (MANAGER/BROKER x CLIENT_PORTAL_ACCOUNT_CREATE, V11) + 6 from ADR-BANKENGINE-001
    // (SUPERADMIN/MANAGER/BROKER x BANK_MATCHING_RUN/READ, V13) + 2 from ADR-BANKENGINE-002
    // (MANAGER/SUPERADMIN x BANK_MATCHING_OVERRIDE, V14) = 231 + 12 from Sprint 10 D10-1
    // (SUPERADMIN/MANAGER/BROKER x AI_USE/AI_DOCUMENT_ANALYZE/AI_SUMMARIZE/AI_DRAFT_MESSAGE,
    // V15) = 243. Full breakdown and PENDING/NOT_ASSIGNED absence verified in RbacSeedIT.
    assertThat(rolePermissionCount).isEqualTo(243);

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
  }
}
