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
 * and Flyway must run every migration (V1-V7) successfully. This is the same physical schema
 * described in 16_POSTGRESQL_SCHEMA_SPECIFICATION.md — no JPA entities exist yet, so this test
 * asserts against raw JDBC metadata rather than a domain model.
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
    assertThat(appliedMigrations).isEqualTo(7);

    Integer tableCount =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'"
                + " AND table_name != 'flyway_schema_history'",
            Integer.class);
    // 38 tables from V1 + 4 (V4) + 1 (V5) + 3 (V6) + 2 (V7) = 48.
    assertThat(tableCount).isEqualTo(48);

    Integer roleCount = jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class);
    assertThat(roleCount).isEqualTo(4);

    Integer permissionCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM permissions", Integer.class);
    assertThat(permissionCount)
        .isEqualTo(110); // full atomic catalog, 14_DEFINITIVE_PERMISSION_CATALOG.md

    Integer documentTypeCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM document_types", Integer.class);
    assertThat(documentTypeCount).isEqualTo(10);
  }
}
