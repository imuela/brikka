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
 * and Flyway must run every migration (V1-V12) successfully. This is the same physical schema
 * described in 16_POSTGRESQL_SCHEMA_SPECIFICATION.md — no JPA entities exist yet, so this test
 * asserts against raw JDBC metadata rather than a domain model. V8 (ADR-IDENTITY-001) makes
 * users.company_id nullable; V9 (ADR-RBAC-001) seeds role_permissions; V10 (Sprint 4) adds
 * document_versions.review_comment; V11 (ADR-PORTAL-AUTH-001, Sprint 7) adds the
 * CLIENT_PORTAL_ACCOUNT_CREATE permission for MANAGER/BROKER; V12 (Sprint 7 decision D3) makes
 * document_versions.uploaded_by nullable and adds uploaded_by_client_id. None add or remove a
 * table; the exact role_permissions content is verified in RbacSeedIT.
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
    assertThat(appliedMigrations).isEqualTo(12);

    Integer tableCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name != 'flyway_schema_history'",
            Integer.class);
    // 38 tables from V1 + 4 (V4) + 1 (V5) + 3 (V6) + 2 (V7) = 48. V8/V9/V10 add no table.
    assertThat(tableCount).isEqualTo(48);

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
        .isEqualTo(111); // 110 full atomic catalog (14_DEFINITIVE_PERMISSION_CATALOG.md) + 1
    // CLIENT_PORTAL_ACCOUNT_CREATE (ADR-PORTAL-AUTH-001, V11)

    Integer documentTypeCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM document_types", Integer.class);
    assertThat(documentTypeCount).isEqualTo(10);

    Integer rolePermissionCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class);
    // 221 APPROVED combinations from ADR-RBAC-001 (81+71+58+11) + 2 from ADR-PORTAL-AUTH-001
    // (MANAGER/BROKER x CLIENT_PORTAL_ACCOUNT_CREATE, V11) = 223. Full breakdown and PENDING/
    // NOT_ASSIGNED absence verified in RbacSeedIT.
    assertThat(rolePermissionCount).isEqualTo(223);

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
  }
}
