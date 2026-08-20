package com.brika.platform.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.CancellationReason;
import com.brika.platform.casemgmt.Case;
import com.brika.platform.casemgmt.CaseService;
import com.brika.platform.casemgmt.CaseStatus;
import com.brika.platform.casemgmt.ParticipationType;
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.communication.Conversation;
import com.brika.platform.communication.ConversationMessageService;
import com.brika.platform.communication.ConversationRepository;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.brika.platform.notification.Notification;
import com.brika.platform.notification.NotificationRepository;
import com.brika.platform.notification.NotificationType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 25: domain events are wired to NotificationService. Real actions produce real IN_APP
 * notifications with the right recipient, type and tenant; the actor is never a recipient; one
 * action yields exactly one notification; a failed operation leaves no notification behind. The
 * integrated E2E test (§17) drives a real action, then reads and marks it read over the HTTP API.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class NotificationEventIntegrationIT {

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

  @Autowired private MockMvc mockMvc;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private ClientRepository clientRepository;
  @Autowired private CaseService caseService;
  @Autowired private ConversationRepository conversationRepository;
  @Autowired private ConversationMessageService conversationMessageService;
  @Autowired private NotificationRepository notificationRepository;

  private record TestPrincipal(String externalIdentityId, User user) {
    String bearer() {
      return "Bearer " + externalIdentityId;
    }
  }

  private UUID newCompany(String taxId) {
    return companyRepository.insert("Co " + taxId, "Co " + taxId, taxId);
  }

  private TestPrincipal createUser(UserRole role, UUID companyId, String emailPrefix) {
    String externalId = "ext-" + UUID.randomUUID();
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                role, companyId, externalId, emailPrefix + "@brika.test", "F", "L"));
    return new TestPrincipal(externalId, user);
  }

  private UUID newClient(UUID companyId, String emailPrefix) {
    return clientRepository.insert(
        companyId, "Cli", "Ent", emailPrefix + "@brika.test", "600000000");
  }

  private List<Notification> notificationsFor(UUID userId) {
    return notificationRepository.findAllByRecipientUserId(userId);
  }

  @Test
  void caseStatusChangeNotifiesAssignedUsersExceptActorWithNoDuplicates() {
    UUID companyId = newCompany("TC-NE1");
    TestPrincipal actor = createUser(UserRole.MANAGER, companyId, "actor-ne1");
    TestPrincipal recipientB = createUser(UserRole.BROKER, companyId, "broker-ne1b");
    TestPrincipal recipientC = createUser(UserRole.BROKER, companyId, "broker-ne1c");

    Case created = caseService.createCase(companyId, actor.user().id(), "MORTGAGE");
    caseService.addClient(created, newClient(companyId, "cli-ne1"), ParticipationType.HOLDER, true);
    caseService.assignUser(created, actor.user().id(), "BROKER");
    caseService.assignUser(created, recipientB.user().id(), "BROKER");
    caseService.assignUser(created, recipientC.user().id(), "BROKER");

    caseService.changeStatus(created, CaseStatus.DOCUMENTATION, actor.user().id(), null);

    List<Notification> actorNotifs = notificationsFor(actor.user().id());
    List<Notification> bNotifs = notificationsFor(recipientB.user().id());
    List<Notification> cNotifs = notificationsFor(recipientC.user().id());

    assertThat(actorNotifs).isEmpty(); // actor excluded
    assertThat(bNotifs).hasSize(1); // exactly one per recipient (no duplicates)
    assertThat(cNotifs).hasSize(1);
    assertThat(bNotifs.get(0).type()).isEqualTo(NotificationType.CASE_STATUS_CHANGED);
    assertThat(bNotifs.get(0).companyId()).isEqualTo(companyId);
    assertThat(bNotifs.get(0).recipientUserId()).isEqualTo(recipientB.user().id());
    assertThat(cNotifs.get(0).type()).isEqualTo(NotificationType.CASE_STATUS_CHANGED);
  }

  @Test
  void messageSendsNotifyCaseAssigneesButNeverTheAuthor() {
    UUID companyId = newCompany("TC-NE2");
    TestPrincipal author = createUser(UserRole.BROKER, companyId, "broker-ne2a");
    TestPrincipal recipientB = createUser(UserRole.BROKER, companyId, "broker-ne2b");
    TestPrincipal recipientC = createUser(UserRole.BROKER, companyId, "broker-ne2c");

    Case created = caseService.createCase(companyId, author.user().id(), "MORTGAGE");
    caseService.assignUser(created, author.user().id(), "BROKER");
    caseService.assignUser(created, recipientB.user().id(), "BROKER");
    caseService.assignUser(created, recipientC.user().id(), "BROKER");

    UUID conversationId = conversationRepository.insert(companyId, created.id(), "INTERNAL");
    Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();

    conversationMessageService.sendFromUser(conversation, author.user().id(), "hello team");

    assertThat(notificationsFor(author.user().id())).isEmpty(); // author not notified
    assertThat(notificationsFor(recipientB.user().id()))
        .singleElement()
        .satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.NEW_MESSAGE));
    assertThat(notificationsFor(recipientC.user().id()))
        .singleElement()
        .satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.NEW_MESSAGE));
  }

  @Test
  void failedCaseTransitionLeavesNoNotificationBehind() {
    UUID companyId = newCompany("TC-NE3");
    TestPrincipal actor = createUser(UserRole.MANAGER, companyId, "mgr-ne3");
    TestPrincipal recipient = createUser(UserRole.BROKER, companyId, "broker-ne3");

    Case created = caseService.createCase(companyId, actor.user().id(), "MORTGAGE");
    caseService.assignUser(created, recipient.user().id(), "BROKER");

    // No client linked -> DOCUMENTATION is rejected (ValidationException).
    assertThatThrownBy(
            () ->
                caseService.changeStatus(
                    created, CaseStatus.DOCUMENTATION, actor.user().id(), null))
        .isInstanceOf(ValidationException.class);

    assertThat(notificationsFor(actor.user().id())).isEmpty();
    assertThat(notificationsFor(recipient.user().id())).isEmpty();
  }

  @Test
  void integratedCaseEventPersistsNotificationThatRecipientReadsOverTheApi() throws Exception {
    UUID companyId = newCompany("TC-NE4");
    TestPrincipal actor = createUser(UserRole.MANAGER, companyId, "mgr-ne4");
    TestPrincipal recipient = createUser(UserRole.BROKER, companyId, "broker-ne4");

    Case created = caseService.createCase(companyId, actor.user().id(), "MORTGAGE");
    caseService.assignUser(created, recipient.user().id(), "BROKER");

    // Actor performs a real business action -> case cancelled -> notification for the assignee.
    caseService.cancel(created, actor.user().id(), CancellationReason.CLIENT_REQUEST, "declined");

    List<Notification> notifications = notificationsFor(recipient.user().id());
    assertThat(notifications).hasSize(1);
    Notification notification = notifications.get(0);
    assertThat(notification.type()).isEqualTo(NotificationType.CASE_CANCELLED);

    String id = notification.id().toString();

    // Recipient lists it via the API.
    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", recipient.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id))
        .andExpect(jsonPath("$[0].type").value(NotificationType.CASE_CANCELLED))
        .andExpect(jsonPath("$[0].readAt").doesNotExist());

    // Unread count reflects one unread notification.
    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", recipient.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(1));

    // Recipient marks it read.
    mockMvc
        .perform(
            patch("/api/v1/notifications/" + id + "/read")
                .header("Authorization", recipient.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").isNotEmpty());

    // After reading, the unread count drops to zero.
    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", recipient.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(0));
  }

  @Test
  void notificationIsNotVisibleOrMutableToAUserInAnotherTenant() throws Exception {
    UUID companyId = newCompany("TC-NE5");
    UUID otherCompanyId = newCompany("TC-NE5B");
    TestPrincipal actor = createUser(UserRole.MANAGER, companyId, "mgr-ne5");
    TestPrincipal recipient = createUser(UserRole.BROKER, companyId, "broker-ne5");
    TestPrincipal foreignUser = createUser(UserRole.BROKER, otherCompanyId, "broker-ne5b");

    Case created = caseService.createCase(companyId, actor.user().id(), "MORTGAGE");
    caseService.assignUser(created, recipient.user().id(), "BROKER");
    caseService.cancel(created, actor.user().id(), CancellationReason.OTHER, "reason");

    Notification notification = notificationsFor(recipient.user().id()).get(0);

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", foreignUser.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(
            patch("/api/v1/notifications/" + notification.id() + "/read")
                .header("Authorization", foreignUser.bearer()))
        .andExpect(status().isNotFound());
  }
}
