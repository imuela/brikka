package com.brika.platform.casemgmt.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.audit.AuditEvent;
import com.brika.platform.audit.AuditEventRepository;
import com.brika.platform.crm.web.CreateClientApiRequest;
import com.brika.platform.crm.web.UpdateClientApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end security/tenant/CASE ASSIGNMENT tests for Clients and Cases (Sprint 3), mirroring
 * IdentityEndpointsIT: real HTTP requests through the actual SecurityFilterChain/controllers.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class CrmCaseEndpointsIT {

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
  @Autowired private AuditEventRepository auditEventRepository;

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

  private UUID createCase(TestPrincipal creator) throws Exception {
    String body = objectMapper.writeValueAsString(new CreateCaseApiRequest("MORTGAGE"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases")
                    .header("Authorization", creator.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  private UUID createClient(TestPrincipal creator, String emailPrefix) throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateClientApiRequest("Cli", "Ent", emailPrefix + "@brika.test", "600000000"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/clients")
                    .header("Authorization", creator.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(response).get("id").asText());
  }

  @Test
  void managerAndBrokerCanBothCreateClientsWithinTenant() throws Exception {
    UUID companyId = companyRepository.insert("Co CC1", "Co CC1", "TC-CC1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cc1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-cc1");

    createClient(manager, "cli-cc1a");
    createClient(broker, "cli-cc1b");

    mockMvc
        .perform(get("/api/v1/clients").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  void clientRoleCannotCreateClients() throws Exception {
    UUID companyId = companyRepository.insert("Co CC2", "Co CC2", "TC-CC2");
    TestPrincipal client = createUser(UserRole.CLIENT, companyId, "client-cc2");

    String body =
        objectMapper.writeValueAsString(new CreateClientApiRequest("A", "B", "a@b.test", "600"));
    mockMvc
        .perform(
            post("/api/v1/clients")
                .header("Authorization", client.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCannotReadClientFromAnotherTenant() throws Exception {
    UUID companyA = companyRepository.insert("Co CC3A", "Co CC3A", "TC-CC3A");
    UUID companyB = companyRepository.insert("Co CC3B", "Co CC3B", "TC-CC3B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-cc3a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-cc3b");
    UUID clientBId = createClient(managerB, "cli-cc3b");

    mockMvc
        .perform(get("/api/v1/clients/" + clientBId).header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void managerAndBrokerCanBothCreateCases() throws Exception {
    UUID companyId = companyRepository.insert("Co CS1", "Co CS1", "TC-CS-E1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cs1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-cs1");

    mockMvc
        .perform(
            post("/api/v1/cases")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateCaseApiRequest("MORTGAGE"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PRESTUDY"));

    mockMvc
        .perform(
            post("/api/v1/cases")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateCaseApiRequest("MORTGAGE"))))
        .andExpect(status().isOk());
  }

  @Test
  void brokerWithoutCaseAssignmentCannotReadCaseEvenWithinOwnTenant() throws Exception {
    UUID companyId = companyRepository.insert("Co CS2", "Co CS2", "TC-CS-E2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cs2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-cs2");
    UUID caseId = createCase(manager);

    mockMvc
        .perform(get("/api/v1/cases/" + caseId).header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void brokerCanReadCaseOnceAssignedByManager() throws Exception {
    UUID companyId = companyRepository.insert("Co CS3", "Co CS3", "TC-CS-E3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cs3");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-cs3");
    UUID caseId = createCase(manager);

    String assignBody =
        objectMapper.writeValueAsString(
            new CreateCaseAssignmentApiRequest(broker.user().id(), "BROKER"));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/assignments")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/cases/" + caseId).header("Authorization", broker.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void brokerCannotAssignUsersToCases() throws Exception {
    UUID companyId = companyRepository.insert("Co CS4", "Co CS4", "TC-CS-E4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cs4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-cs4");
    TestPrincipal broker2 = createUser(UserRole.BROKER, companyId, "broker-cs4b");
    UUID caseId = createCase(manager);

    String assignBody =
        objectMapper.writeValueAsString(
            new CreateCaseAssignmentApiRequest(broker2.user().id(), "BROKER"));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/assignments")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignBody))
        .andExpect(status().isForbidden());
  }

  @Test
  void managerCannotReadCaseFromAnotherTenant() throws Exception {
    UUID companyA = companyRepository.insert("Co CS5A", "Co CS5A", "TC-CS-E5A");
    UUID companyB = companyRepository.insert("Co CS5B", "Co CS5B", "TC-CS-E5B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-cs5a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-cs5b");
    UUID caseBId = createCase(managerB);

    mockMvc
        .perform(get("/api/v1/cases/" + caseBId).header("Authorization", managerA.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void superadminWithoutSupportSessionCannotAccessCasesEndpoint() throws Exception {
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-cs6");

    mockMvc
        .perform(get("/api/v1/cases").header("Authorization", superadmin.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void addingAndRemovingCaseParticipantWorksForAssignedBroker() throws Exception {
    UUID companyId = companyRepository.insert("Co CS7", "Co CS7", "TC-CS-E7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cs7");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-cs7");
    UUID caseId = createCase(manager);
    UUID clientId = createClient(manager, "cli-cs7");

    String assignBody =
        objectMapper.writeValueAsString(
            new CreateCaseAssignmentApiRequest(broker.user().id(), "BROKER"));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/assignments")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignBody))
        .andExpect(status().isOk());

    String addClientBody =
        objectMapper.writeValueAsString(new CaseClientApiRequest(clientId, "HOLDER", true));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/clients")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addClientBody))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/clients").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].participationType").value("HOLDER"));

    mockMvc
        .perform(
            delete("/api/v1/cases/" + caseId + "/clients/" + clientId)
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/clients").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void statusChangeIsRecordedInActivitiesAndVisibleViaEndpoint() throws Exception {
    UUID companyId = companyRepository.insert("Co CS8", "Co CS8", "TC-CS-E8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cs8");
    UUID caseId = createCase(manager);
    UUID clientId = createClient(manager, "cli-cs8");

    String addClientBody =
        objectMapper.writeValueAsString(new CaseClientApiRequest(clientId, "HOLDER", true));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/clients")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(addClientBody))
        .andExpect(status().isOk());

    String statusBody =
        objectMapper.writeValueAsString(new ChangeCaseStatusApiRequest("DOCUMENTATION", null));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/status")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DOCUMENTATION"));

    mockMvc
        .perform(
            get("/api/v1/cases/" + caseId + "/activities")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));
  }

  @Test
  void cancelRequiresACatalogReason() throws Exception {
    UUID companyId = companyRepository.insert("Co CS9", "Co CS9", "TC-CS-E9");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cs9");
    UUID caseId = createCase(manager);

    String badBody =
        objectMapper.writeValueAsString(new CancelCaseApiRequest("NOT_A_REAL_REASON", null));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/cancel")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(badBody))
        .andExpect(status().isBadRequest());

    String goodBody = objectMapper.writeValueAsString(new CancelCaseApiRequest("ABANDONED", null));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/cancel")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(goodBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void updatingAClientWritesAnAuditEvent() throws Exception {
    UUID companyId = companyRepository.insert("Co AUD2", "Co AUD2", "TC-AUD2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-aud2");
    UUID clientId = createClient(manager, "cli-aud2");

    String updateBody =
        objectMapper.writeValueAsString(
            new UpdateClientApiRequest("Updated", "Name", "updated@brika.test", "600000001"));
    mockMvc
        .perform(
            patch("/api/v1/clients/" + clientId)
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk());

    AuditEvent event =
        auditEventRepository.findAll().stream()
            .filter(e -> "CLIENT_UPDATED".equals(e.action()) && clientId.equals(e.resourceId()))
            .findFirst()
            .orElseThrow();
    assertThat(event.companyId()).isEqualTo(companyId);
    assertThat(event.actorUserId()).isEqualTo(manager.user().id());
    assertThat(event.resourceType()).isEqualTo("CLIENT");
  }

  @Test
  void caseLifecycleActionsWriteAuditEvents() throws Exception {
    UUID companyId = companyRepository.insert("Co AUD3", "Co AUD3", "TC-AUD3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-aud3");
    UUID caseId = createCase(manager);

    String updateBody = objectMapper.writeValueAsString(new UpdateCaseApiRequest("MORTGAGE"));
    mockMvc
        .perform(
            patch("/api/v1/cases/" + caseId)
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk());

    String statusBody =
        objectMapper.writeValueAsString(new ChangeCaseStatusApiRequest("DOCUMENTATION", null));
    UUID clientId = createClient(manager, "cli-aud3");
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/clients")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CaseClientApiRequest(clientId, "HOLDER", true))))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/status")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusBody))
        .andExpect(status().isOk());

    String cancelBody =
        objectMapper.writeValueAsString(new CancelCaseApiRequest("ABANDONED", null));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/cancel")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cancelBody))
        .andExpect(status().isOk());

    String reopenBody =
        objectMapper.writeValueAsString(new ReopenCaseApiRequest("Reactivated", "PRESTUDY"));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/reopen")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reopenBody))
        .andExpect(status().isOk());

    List<AuditEvent> events =
        auditEventRepository.findAll().stream().filter(e -> caseId.equals(e.resourceId())).toList();
    assertThat(events).extracting(AuditEvent::action).contains("CASE_UPDATED");
    assertThat(events).extracting(AuditEvent::action).contains("CASE_STATUS_CHANGED");
    assertThat(events).extracting(AuditEvent::action).contains("CASE_CANCELLED");
    assertThat(events).extracting(AuditEvent::action).contains("CASE_REOPENED");
    assertThat(events).allMatch(e -> companyId.equals(e.companyId()));
    assertThat(events).allMatch(e -> "CASE".equals(e.resourceType()));
  }
}
