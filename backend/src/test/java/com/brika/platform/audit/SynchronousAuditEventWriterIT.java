package com.brika.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.common.observability.CorrelationIdFilter;
import com.brika.platform.identity.CompanyRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 11/12: verifies SynchronousAuditEventWriter delegates to AuditEventRepository and every
 * field survives the round trip, including {@code requestId} captured internally from MDC (Sprint
 * 12 — the interface no longer takes it as a parameter). Since Sprint 12 (D12-2), domain
 * controllers call this writer — see {@code ADR-AUDIT-002}.
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

    MDC.put(CorrelationIdFilter.MDC_KEY, "req-writer-1");
    try {
      auditEventWriter.write(
          companyId,
          null,
          null,
          "WRITER_TEST_ACTION",
          "WRITER_TEST_RESOURCE",
          resourceId,
          "{\"via\":\"writer\"}");
    } finally {
      MDC.remove(CorrelationIdFilter.MDC_KEY);
    }

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
