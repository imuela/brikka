package com.brika.platform.casemgmt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.brika.platform.activity.Activity;
import com.brika.platform.activity.ActivityRepository;
import com.brika.platform.common.error.ResourceNotFoundException;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Business-rule tests for CaseService against 13_DEFINITIVE_WORKFLOW_SPECIFICATION.md. */
@Testcontainers
@SpringBootTest
class CaseServiceIT {

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
  @Autowired private ClientRepository clientRepository;
  @Autowired private CaseService caseService;
  @Autowired private CaseClientRepository caseClientRepository;
  @Autowired private CaseStatusHistoryRepository caseStatusHistoryRepository;
  @Autowired private ActivityRepository activityRepository;

  private UUID newCompany(String taxId) {
    return companyRepository.insert("Co " + taxId, "Co " + taxId, taxId);
  }

  private User newManager(UUID companyId, String emailPrefix) {
    return userProvisioningService.createUser(
        new CreateUserCommand(
            UserRole.MANAGER,
            companyId,
            "ext-" + UUID.randomUUID(),
            emailPrefix + "@brika.test",
            "M",
            "Gr"));
  }

  private UUID newClient(UUID companyId, String emailPrefix) {
    return clientRepository.insert(
        companyId, "Cli", "Ent", emailPrefix + "@brika.test", "600000000");
  }

  @Test
  void createCaseStartsAtPrestudyAndRecordsActivity() {
    UUID companyId = newCompany("TC-CS1");
    User manager = newManager(companyId, "mgr-cs1");

    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");

    assertThat(created.status()).isEqualTo(CaseStatus.PRESTUDY);
    assertThat(created.reference()).isNotBlank();
    List<Activity> activities = activityRepository.findAllByCaseId(created.id());
    assertThat(activities).hasSize(1);
    assertThat(activities.get(0).activityType()).isEqualTo("CaseCreated");
  }

  @Test
  void documentationTransitionRequiresAtLeastOneClient() {
    UUID companyId = newCompany("TC-CS2");
    User manager = newManager(companyId, "mgr-cs2");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");

    assertThatThrownBy(
            () -> caseService.changeStatus(created, CaseStatus.DOCUMENTATION, manager.id(), null))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void documentationTransitionSucceedsOnceClientLinkedAndHistoryPlusActivityAreRecorded() {
    UUID companyId = newCompany("TC-CS3");
    User manager = newManager(companyId, "mgr-cs3");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    UUID clientId = newClient(companyId, "cli-cs3");
    caseService.addClient(created, clientId, ParticipationType.HOLDER, true);

    Case updated = caseService.changeStatus(created, CaseStatus.DOCUMENTATION, manager.id(), null);

    assertThat(updated.status()).isEqualTo(CaseStatus.DOCUMENTATION);
    assertThat(caseStatusHistoryRepository.countByCaseId(created.id())).isEqualTo(1);
    List<Activity> activities = activityRepository.findAllByCaseId(created.id());
    assertThat(activities).extracting(Activity::activityType).contains("CaseStatusChanged");
  }

  @Test
  void skippingStagesIsRejected() {
    UUID companyId = newCompany("TC-CS4");
    User manager = newManager(companyId, "mgr-cs4");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");

    assertThatThrownBy(
            () -> caseService.changeStatus(created, CaseStatus.OFFER, manager.id(), null))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void statusEndpointRejectsCancelledAsATarget() {
    UUID companyId = newCompany("TC-CS5");
    User manager = newManager(companyId, "mgr-cs5");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");

    assertThatThrownBy(
            () -> caseService.changeStatus(created, CaseStatus.CANCELLED, manager.id(), "reason"))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void cancelSetsCancelledAtAndRecordsActivity() {
    UUID companyId = newCompany("TC-CS6");
    User manager = newManager(companyId, "mgr-cs6");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");

    Case cancelled =
        caseService.cancel(
            created, manager.id(), CancellationReason.CLIENT_REQUEST, "changed mind");

    assertThat(cancelled.status()).isEqualTo(CaseStatus.CANCELLED);
    assertThat(cancelled.cancelledAt()).isNotNull();
    List<Activity> activities = activityRepository.findAllByCaseId(created.id());
    assertThat(activities).extracting(Activity::activityType).contains("CaseCancelled");
  }

  @Test
  void cancellingAnAlreadyTerminalCaseIsRejected() {
    UUID companyId = newCompany("TC-CS7");
    User manager = newManager(companyId, "mgr-cs7");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Case cancelled = caseService.cancel(created, manager.id(), CancellationReason.OTHER, null);

    assertThatThrownBy(
            () -> caseService.cancel(cancelled, manager.id(), CancellationReason.OTHER, null))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void reopenClearsCancelledAtAndMovesToTargetStatus() {
    UUID companyId = newCompany("TC-CS8");
    User manager = newManager(companyId, "mgr-cs8");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Case cancelled = caseService.cancel(created, manager.id(), CancellationReason.DUPLICATE, null);

    Case reopened =
        caseService.reopen(cancelled, manager.id(), "reactivated by client", CaseStatus.PRESTUDY);

    assertThat(reopened.status()).isEqualTo(CaseStatus.PRESTUDY);
    assertThat(reopened.cancelledAt()).isNull();
    List<Activity> activities = activityRepository.findAllByCaseId(created.id());
    assertThat(activities).extracting(Activity::activityType).contains("CaseReopened");
  }

  @Test
  void reopeningToATerminalTargetIsRejected() {
    UUID companyId = newCompany("TC-CS9");
    User manager = newManager(companyId, "mgr-cs9");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    Case cancelled = caseService.cancel(created, manager.id(), CancellationReason.OTHER, null);

    assertThatThrownBy(
            () -> caseService.reopen(cancelled, manager.id(), "reason", CaseStatus.COMPLETED))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void reopeningANonTerminalCaseIsRejected() {
    UUID companyId = newCompany("TC-CS10");
    User manager = newManager(companyId, "mgr-cs10");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");

    assertThatThrownBy(
            () -> caseService.reopen(created, manager.id(), "reason", CaseStatus.DOCUMENTATION))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void addingAClientFromAnotherTenantIsRejected() {
    UUID companyId = newCompany("TC-CS11");
    UUID otherCompanyId = newCompany("TC-CS11B");
    User manager = newManager(companyId, "mgr-cs11");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    UUID foreignClientId = newClient(otherCompanyId, "cli-cs11b");

    assertThatThrownBy(
            () -> caseService.addClient(created, foreignClientId, ParticipationType.HOLDER, true))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void addingTheSameClientTwiceIsRejected() {
    UUID companyId = newCompany("TC-CS12");
    User manager = newManager(companyId, "mgr-cs12");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    UUID clientId = newClient(companyId, "cli-cs12");
    caseService.addClient(created, clientId, ParticipationType.HOLDER, true);

    assertThatThrownBy(
            () -> caseService.addClient(created, clientId, ParticipationType.CO_HOLDER, false))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void removingAClientThatIsNotLinkedIsRejected() {
    UUID companyId = newCompany("TC-CS13");
    User manager = newManager(companyId, "mgr-cs13");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    UUID clientId = newClient(companyId, "cli-cs13");

    assertThatThrownBy(() -> caseService.removeClient(created, clientId))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void assigningAUserFromAnotherTenantIsRejected() {
    UUID companyId = newCompany("TC-CS14");
    UUID otherCompanyId = newCompany("TC-CS14B");
    User manager = newManager(companyId, "mgr-cs14");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    User foreignUser = newManager(otherCompanyId, "mgr-cs14b");

    assertThatThrownBy(() -> caseService.assignUser(created, foreignUser.id(), "BROKER"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void fullHappyPathReachesCompletedAndRecordsCaseCompletedActivity() {
    UUID companyId = newCompany("TC-CS15");
    User manager = newManager(companyId, "mgr-cs15");
    Case created = caseService.createCase(companyId, manager.id(), "MORTGAGE");
    UUID clientId = newClient(companyId, "cli-cs15");
    caseService.addClient(created, clientId, ParticipationType.HOLDER, true);

    Case c = caseService.changeStatus(created, CaseStatus.DOCUMENTATION, manager.id(), null);
    c = caseService.changeStatus(c, CaseStatus.ANALYSIS, manager.id(), null);
    c = caseService.changeStatus(c, CaseStatus.BANK_SEARCH, manager.id(), null);
    c = caseService.changeStatus(c, CaseStatus.BANK_SUBMISSION, manager.id(), null);
    c = caseService.changeStatus(c, CaseStatus.BANK_REVIEW, manager.id(), null);
    c = caseService.changeStatus(c, CaseStatus.OFFER, manager.id(), null);
    c = caseService.changeStatus(c, CaseStatus.FORMALIZATION, manager.id(), null);
    c = caseService.changeStatus(c, CaseStatus.COMPLETED, manager.id(), null);

    assertThat(c.status()).isEqualTo(CaseStatus.COMPLETED);
    assertThat(caseStatusHistoryRepository.countByCaseId(created.id())).isEqualTo(8);
    List<Activity> activities = activityRepository.findAllByCaseId(created.id());
    assertThat(activities).extracting(Activity::activityType).contains("CaseCompleted");
  }
}
