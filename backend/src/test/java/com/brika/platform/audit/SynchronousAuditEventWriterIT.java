package com.brika.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.identity.CompanyRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 11: verifies SynchronousAuditEventWriter delegates to AuditEventRepository and every field
 * survives the round trip. No caller wires this writer to any domain service yet (D11-5) — this
 * test exercises the writer directly, the only way it can be exercised in this sprint.
 */
@Testcontainers
@SpringBootTest
class SynchronousAuditEventWriterIT {

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

  @Autowired private CompanyRepository companyRepository;
  @Autowired private AuditEventWriter auditEventWriter;
  @Autowired private AuditEventRepository auditEventRepository;

  @Test
  void writeDelegatesToRepositoryAndPreservesAllFields() {
    UUID companyId = companyRepository.insert("Co SW1", "Co SW1", "TC-SW1");
    UUID resourceId = UUID.randomUUID();

    int before = auditEventRepository.findAll().size();

    auditEventWriter.write(
        companyId,
        null,
        null,
        "WRITER_TEST_ACTION",
        "WRITER_TEST_RESOURCE",
        resourceId,
        "req-writer-1",
        "{\"via\":\"writer\"}");

    var all = auditEventRepository.findAll();
    assertThat(all).hasSize(before + 1);

    AuditEvent written =
        all.stream().filter(e -> "WRITER_TEST_ACTION".equals(e.action())).findFirst().orElseThrow();
    assertThat(written.companyId()).isEqualTo(companyId);
    assertThat(written.resourceType()).isEqualTo("WRITER_TEST_RESOURCE");
    assertThat(written.resourceId()).isEqualTo(resourceId);
    assertThat(written.requestId()).isEqualTo("req-writer-1");
    assertThat(written.metadataJson()).contains("via");
  }

  @Test
  void writerImplementationIsSynchronous() {
    assertThat(auditEventWriter).isInstanceOf(SynchronousAuditEventWriter.class);
  }
}
