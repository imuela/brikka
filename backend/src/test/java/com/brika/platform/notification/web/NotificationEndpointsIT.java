package com.brika.platform.notification.web;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.brika.platform.notification.Notification;
import com.brika.platform.notification.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
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
 * Sprint 8: Notifications. ADR-NOTIF-001. Creation is an internal capability (NotificationService).
 * Sprint 25 wired real domain events to it (ADR-NOTIF-002); these tests still invoke the service
 * directly to exercise the documented read/manage HTTP surface (§17C) in isolation. D8-1: IN_APP
 * dispatched synchronously. D8-2: EMAIL dispatched via NoOpEmailSender (no provider approved) —
 * recorded as a structural FAILED delivery, never silently pretended to succeed.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class NotificationEndpointsIT {

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
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private NotificationService notificationService;

  private record TestPrincipal(String externalIdentityId, User user) {
    String bearer() {
      return "Bearer " + externalIdentityId;
    }
  }

  private TestPrincipal createUser(UserRole role, UUID companyId, String emailPrefix) {
    String externalId = "ext-" + UUID.randomUUID();
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                role, companyId, externalId, emailPrefix + "@brika.test", "First", "Last"));
    return new TestPrincipal(externalId, user);
  }

  @Test
  void createdNotificationIsVisibleToRecipientAndMarkableRead() throws Exception {
    UUID companyId = companyRepository.insert("Co N1", "Co N1", "TC-N1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-n1");

    Notification notification =
        notificationService.create(
            companyId, manager.user().id(), null, "TASK_ASSIGNED", Map.of("title", "hello"));

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(notification.id().toString()))
        .andExpect(jsonPath("$[0].readAt").value(nullValue()));

    mockMvc
        .perform(
            patch("/api/v1/notifications/" + notification.id() + "/read")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.readAt").value(notNullValue()));
  }

  @Test
  void inAppDeliveryIsSentImmediatelyAndEmailIsStructurallyFailedWithoutProvider()
      throws Exception {
    UUID companyId = companyRepository.insert("Co N2", "Co N2", "TC-N2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-n2");

    Notification notification =
        notificationService.create(
            companyId, broker.user().id(), null, "CASE_STATUS_CHANGED", Map.of());

    String response =
        mockMvc
            .perform(
                get("/api/v1/notifications/" + notification.id() + "/deliveries")
                    .header("Authorization", broker.bearer()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode deliveries = objectMapper.readTree(response);
    boolean inAppSent = false;
    boolean emailFailedWithReason = false;
    for (JsonNode delivery : deliveries) {
      if ("IN_APP".equals(delivery.get("channel").asText())) {
        inAppSent = "SENT".equals(delivery.get("status").asText());
      }
      if ("EMAIL".equals(delivery.get("channel").asText())) {
        emailFailedWithReason =
            "FAILED".equals(delivery.get("status").asText())
                && !delivery.get("failedReason").isNull();
      }
    }
    org.assertj.core.api.Assertions.assertThat(inAppSent).isTrue();
    org.assertj.core.api.Assertions.assertThat(emailFailedWithReason).isTrue();
  }

  @Test
  void notificationIsNotVisibleToAnotherUserInTheSameTenant() throws Exception {
    UUID companyId = companyRepository.insert("Co N3", "Co N3", "TC-N3");
    TestPrincipal recipient = createUser(UserRole.MANAGER, companyId, "manager-n3");
    TestPrincipal otherManager = createUser(UserRole.MANAGER, companyId, "manager-n3b");

    Notification notification =
        notificationService.create(
            companyId, recipient.user().id(), null, "TASK_ASSIGNED", Map.of());

    mockMvc
        .perform(
            patch("/api/v1/notifications/" + notification.id() + "/read")
                .header("Authorization", otherManager.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void superadminCannotAccessNotifications() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-n4");

    mockMvc
        .perform(get("/api/v1/notifications").header("Authorization", superadmin.bearer()))
        .andExpect(status().isForbidden());
  }
}
