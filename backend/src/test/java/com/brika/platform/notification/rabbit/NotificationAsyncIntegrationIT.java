package com.brika.platform.notification.rabbit;

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
import com.brika.platform.common.error.ValidationException;
import com.brika.platform.communication.Conversation;
import com.brika.platform.communication.ConversationMessageService;
import com.brika.platform.communication.ConversationRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.brika.platform.notification.Notification;
import com.brika.platform.notification.NotificationRepository;
import com.brika.platform.notification.NotificationType;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * Sprint 26. End-to-end over the real RabbitMQ transport: a domain action publishes {@code
 * notification.requested} after commit, the broker delivers it to the consumer, and the consumer
 * writes the notification. Because it is asynchronous the tests await the notification.
 *
 * <p>Activates {@code brika.notifications.transport=rabbitmq} and connects Spring AMQP to a real
 * RabbitMQ broker (not a mock). It reuses the local {@code brika-rabbitmq} service from {@code
 * docs/docker-compose.yml} (host-mapped port 25672) instead of a Testcontainers broker: the only
 * local host has ~2 GB of RAM, which is too little to boot a Testcontainers RabbitMQ (it hits the
 * {@code system_memory_high_watermark} alarm and never opens the AMQP port) alongside Postgres and
 * the Maven JVM. A Testcontainers RabbitMQ is the preferred option on a host with enough memory;
 * here a real, already-running broker is used. The notification queue is purged before each test so
 * a stale message cannot pollute a later test.
 *
 * <p>Covers: full flow, recipient/type/tenant correctness, no duplicate notifications, multi-tenant
 * isolation, reading over the HTTP API, and the guarantee that a failed business action leaves no
 * notification behind. The Portal-client recipient path is exercised in unit form by {@link
 * NotificationRequestedConsumerTest} and end-to-end (sync transport) by the Sprint 25 document
 * event IT.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class NotificationAsyncIntegrationIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    // The RabbitMQ broker is the local docker-compose service (application.yml defaults:
    // localhost:25672, brika/brika_dev_password). Only the async transport is turned on here.
    registry.add("brika.notifications.transport", () -> "rabbitmq");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private CaseService caseService;
  @Autowired private ConversationRepository conversationRepository;
  @Autowired private ConversationMessageService conversationMessageService;
  @Autowired private NotificationRepository notificationRepository;
  @Autowired private RabbitTemplate rabbitTemplate;

  @BeforeEach
  void purgeNotificationsQueue() {
    // Start each test with a clean queue so a message left over from a previous run/test cannot be
    // attributed to the current one (the tests use fresh recipient ids, so a leftover would only
    // stall an await; purging makes the suite deterministic and repeatable).
    new RabbitAdmin(rabbitTemplate.getConnectionFactory())
        .purgeQueue(RabbitMqConfig.QUEUE_NOTIFICATIONS, false);
  }

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

  private List<Notification> notificationsFor(UUID userId) {
    return notificationRepository.findAllByRecipientUserId(userId);
  }

  /** Polls the DB until {@code userId} has at least {@code expected} notifications (async). */
  private List<Notification> awaitNotifications(UUID userId, int expected)
      throws InterruptedException {
    long deadline = System.currentTimeMillis() + Duration.ofSeconds(20).toMillis();
    List<Notification> current = notificationsFor(userId);
    while (current.size() < expected && System.currentTimeMillis() < deadline) {
      Thread.sleep(150);
      current = notificationsFor(userId);
    }
    return current;
  }

  @Test
  void asyncCaseEventReachesRecipientThroughTheBroker() throws Exception {
    UUID companyId = newCompany("TA-1");
    TestPrincipal actor = createUser(UserRole.MANAGER, companyId, "mgr-ta1");
    TestPrincipal recipient = createUser(UserRole.BROKER, companyId, "broker-ta1");

    Case created = caseService.createCase(companyId, actor.user().id(), "MORTGAGE");
    caseService.assignUser(created, recipient.user().id(), "BROKER");

    caseService.cancel(created, actor.user().id(), CancellationReason.CLIENT_REQUEST, "declined");

    List<Notification> notifications = awaitNotifications(recipient.user().id(), 1);
    assertThat(notifications).hasSize(1); // no duplicates across the async path
    Notification notification = notifications.get(0);
    assertThat(notification.type()).isEqualTo(NotificationType.CASE_CANCELLED);
    assertThat(notification.companyId()).isEqualTo(companyId);
    assertThat(notification.recipientUserId()).isEqualTo(recipient.user().id());

    // Actor never notified.
    assertThat(notificationsFor(actor.user().id())).isEmpty();

    // Recipient reads it over the API.
    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", recipient.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(notification.id().toString()))
        .andExpect(jsonPath("$[0].type").value(NotificationType.CASE_CANCELLED));

    mockMvc
        .perform(
            patch("/api/v1/notifications/" + notification.id() + "/read")
                .header("Authorization", recipient.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").isNotEmpty());

    mockMvc
        .perform(
            get("/api/v1/notifications/unread-count").header("Authorization", recipient.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.count").value(0));
  }

  @Test
  void asyncMessageEventNotifiesAssigneesButNotTheAuthor() throws Exception {
    UUID companyId = newCompany("TA-2");
    TestPrincipal author = createUser(UserRole.BROKER, companyId, "broker-ta2a");
    TestPrincipal recipientB = createUser(UserRole.BROKER, companyId, "broker-ta2b");

    Case created = caseService.createCase(companyId, author.user().id(), "MORTGAGE");
    caseService.assignUser(created, author.user().id(), "BROKER");
    caseService.assignUser(created, recipientB.user().id(), "BROKER");

    UUID conversationId = conversationRepository.insert(companyId, created.id(), "INTERNAL");
    Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();
    conversationMessageService.sendFromUser(conversation, author.user().id(), "hello async");

    assertThat(awaitNotifications(recipientB.user().id(), 1))
        .singleElement()
        .satisfies(n -> assertThat(n.type()).isEqualTo(NotificationType.NEW_MESSAGE));
    assertThat(notificationsFor(author.user().id())).isEmpty();
  }

  @Test
  void failedBusinessActionLeavesNoNotificationBehind() throws Exception {
    UUID companyId = newCompany("TA-4");
    TestPrincipal actor = createUser(UserRole.MANAGER, companyId, "mgr-ta4");
    TestPrincipal recipient = createUser(UserRole.BROKER, companyId, "broker-ta4");

    Case created = caseService.createCase(companyId, actor.user().id(), "MORTGAGE");
    caseService.assignUser(created, recipient.user().id(), "BROKER");

    // No client linked -> DOCUMENTATION is rejected; the transaction rolls back before commit, so
    // nothing is published (after-commit never fires) and no notification may appear
    // asynchronously.
    assertThatThrownBy(
            () ->
                caseService.changeStatus(
                    created, CaseStatus.DOCUMENTATION, actor.user().id(), null))
        .isInstanceOf(ValidationException.class);

    Thread.sleep(1500); // give the async transport time to (not) produce anything
    assertThat(notificationsFor(recipient.user().id())).isEmpty();
  }

  @Test
  void notificationIsNotVisibleToAUserInAnotherTenant() throws Exception {
    UUID companyId = newCompany("TA-5");
    UUID otherCompanyId = newCompany("TA-5B");
    TestPrincipal actor = createUser(UserRole.MANAGER, companyId, "mgr-ta5");
    TestPrincipal recipient = createUser(UserRole.BROKER, companyId, "broker-ta5");
    TestPrincipal foreignUser = createUser(UserRole.BROKER, otherCompanyId, "broker-ta5b");

    Case created = caseService.createCase(companyId, actor.user().id(), "MORTGAGE");
    caseService.assignUser(created, recipient.user().id(), "BROKER");
    caseService.cancel(created, actor.user().id(), CancellationReason.OTHER, "reason");

    Notification notification = awaitNotifications(recipient.user().id(), 1).get(0);
    assertThat(notification.companyId()).isEqualTo(companyId);

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
