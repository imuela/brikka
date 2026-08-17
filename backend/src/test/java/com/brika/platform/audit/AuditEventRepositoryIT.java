package com.brika.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import java.util.List;
import java.util.Optional;
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
 * ADR-AUDIT-001 / Sprint 11: mechanical persistence tests for audit_events — insert-only, immutable
 * (no update/delete method exists on the repository).
 */
@Testcontainers
@SpringBootTest
class AuditEventRepositoryIT {

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
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private AuditEventRepository auditEventRepository;

  private UUID newCompany(String taxId) {
    return companyRepository.insert("Co " + taxId, "Co " + taxId, taxId);
  }

  private User newSuperadmin(String emailPrefix) {
    return userProvisioningService.createUser(
        new CreateUserCommand(
            UserRole.SUPERADMIN,
            null,
            "ext-" + UUID.randomUUID(),
            emailPrefix + "@brika.test",
            "First",
            "Last"));
  }

  @Test
  void insertAndFindByIdRoundTripsAllColumns() {
    UUID companyId = newCompany("TC-AE1");
    User actor = newSuperadmin("superadmin-ae1");
    UUID resourceId = UUID.randomUUID();

    UUID id =
        auditEventRepository.insert(
            companyId,
            actor.id(),
            null,
            "TEST_ACTION",
            "TEST_RESOURCE",
            resourceId,
            "req-123",
            "{\"note\":\"hello\"}");

    Optional<AuditEvent> found = auditEventRepository.findById(id);
    assertThat(found).isPresent();
    AuditEvent event = found.orElseThrow();
    assertThat(event.companyId()).isEqualTo(companyId);
    assertThat(event.actorUserId()).isEqualTo(actor.id());
    assertThat(event.actorClientId()).isNull();
    assertThat(event.action()).isEqualTo("TEST_ACTION");
    assertThat(event.resourceType()).isEqualTo("TEST_RESOURCE");
    assertThat(event.resourceId()).isEqualTo(resourceId);
    assertThat(event.requestId()).isEqualTo("req-123");
    assertThat(event.metadataJson()).contains("hello");
    assertThat(event.createdAt()).isNotNull();
  }

  @Test
  void insertWithNullCompanyIdIsAllowedForGlobalEvents() {
    UUID id =
        auditEventRepository.insert(
            null, null, null, "GLOBAL_ACTION", "PLATFORM", null, null, "{}");

    AuditEvent event = auditEventRepository.findById(id).orElseThrow();
    assertThat(event.companyId()).isNull();
  }

  @Test
  void findByIdReturnsEmptyForUnknownId() {
    assertThat(auditEventRepository.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void findAllOrdersByCreatedAtDescending() throws InterruptedException {
    UUID companyId = newCompany("TC-AE4");
    UUID first =
        auditEventRepository.insert(
            companyId, null, null, "FIRST_ACTION", "TEST_RESOURCE", null, null, "{}");
    Thread.sleep(5);
    UUID second =
        auditEventRepository.insert(
            companyId, null, null, "SECOND_ACTION", "TEST_RESOURCE", null, null, "{}");

    List<AuditEvent> all = auditEventRepository.findAll();
    int firstIndex = indexOf(all, first);
    int secondIndex = indexOf(all, second);
    assertThat(secondIndex).isLessThan(firstIndex);
  }

  private int indexOf(List<AuditEvent> events, UUID id) {
    for (int i = 0; i < events.size(); i++) {
      if (events.get(i).id().equals(id)) {
        return i;
      }
    }
    throw new AssertionError("Event " + id + " not found in list");
  }
}
