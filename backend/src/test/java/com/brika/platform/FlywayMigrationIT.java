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
 * and Flyway must run every migration (V1-V9) successfully. This is the same physical schema
 * described in 16_POSTGRESQL_SCHEMA_SPECIFICATION.md — no JPA entities exist yet, so this test
 * asserts against raw JDBC metadata rather than a domain model. V8 (ADR-IDENTITY-001) makes
 * users.company_id nullable; V9 (ADR-RBAC-001) seeds role_permissions. Neither adds or removes a
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
    assertThat(appliedMigrations).isEqualTo(9);

    Integer tableCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name != 'flyway_schema_history'",
            Integer.class);
    // 38 tables from V1 + 4 (V4) + 1 (V5) + 3 (V6) + 2 (V7) = 48. V8/V9 add no table.
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
        .isEqualTo(110); // full atomic catalog, 14_DEFINITIVE_PERMISSION_CATALOG.md

    Integer documentTypeCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM document_types", Integer.class);
    assertThat(documentTypeCount).isEqualTo(10);

    Integer rolePermissionCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM role_permissions", Integer.class);
    // 221 APPROVED combinations from ADR-RBAC-001 (81+71+58+11); full breakdown and PENDING/
    // NOT_ASSIGNED absence verified in RbacSeedIT.
    assertThat(rolePermissionCount).isEqualTo(221);
  }
}
