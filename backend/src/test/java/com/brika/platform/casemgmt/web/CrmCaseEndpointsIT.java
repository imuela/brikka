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
            new CreateClientApiRequest(
                "Cli",
                "Ent",
                emailPrefix + "@brika.test",
                "600000000",
                null,
                null,
                null,
                null,
                null,
                null));
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
        objectMapper.writeValueAsString(
            new CreateClientApiRequest(
                "A", "B", "a@b.test", "600", null, null, null, null, null, null));
    mockMvc
        .perform(
            post("/api/v1/clients")
                .header("Authorization", client.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void extendedClientAttributesRoundTripThroughCreateAndGet() throws Exception {
    // Sprint 27, Bloque 3 (FUNCTIONAL_SPECIFICATION.md §6): document, date of birth, nationality,
    // address and employment status are persisted and returned alongside the core fields.
    UUID companyId = companyRepository.insert("Co CC8", "Co CC8", "TC-CC8");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cc8");

    String body =
        objectMapper.writeValueAsString(
            new CreateClientApiRequest(
                "Elena",
                "Nito",
                "elena@brika.test",
                "600000000",
                "DNI",
                "12345678A",
                java.time.LocalDate.of(1990, 5, 4),
                "Española",
                "Calle Mayor 1",
                "EMPLEADO"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/clients")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.documentType").value("DNI"))
            .andExpect(jsonPath("$.documentNumber").value("12345678A"))
            .andExpect(jsonPath("$.dateOfBirth").value("1990-05-04"))
            .andExpect(jsonPath("$.nationality").value("Española"))
            .andExpect(jsonPath("$.address").value("Calle Mayor 1"))
            .andExpect(jsonPath("$.employmentStatus").value("EMPLEADO"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID clientId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(get("/api/v1/clients/" + clientId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.documentNumber").value("12345678A"))
        .andExpect(jsonPath("$.employmentStatus").value("EMPLEADO"));
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
    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — the tenant is resolved from the resource,
    // so /api/v1/cases is now accessible (200) instead of the pre-sprint 403. No SUPPORT_SESSION
    // is required. The array size varies with other methods in this class, so only accessibility
    // (200 + JSON array) is asserted.
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-cs6");

    mockMvc
        .perform(get("/api/v1/cases").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void superadminReadsCasesClientsAndActivityGloballyAcrossCompanies() throws Exception {
    // Sprint 27 (ADR-RBAC-002): SUPERADMIN is GLOBAL — the tenant is resolved from the resource, so
    // it reads a case (list + single), the case's client, and the activity dashboard from a company
    // that is not its own (it has none). Tenant isolation for real tenant users is unchanged.
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-cs9");
    UUID companyA = companyRepository.insert("Co SA", "Co SA", "TC-SA");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-sa");
    UUID caseId = createCase(managerA);
    UUID clientId = createClient(managerA, "cli-sa");

    mockMvc
        .perform(get("/api/v1/cases").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + caseId + "')]").exists());

    mockMvc
        .perform(get("/api/v1/cases/" + caseId).header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(caseId.toString()));

    mockMvc
        .perform(get("/api/v1/clients").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id=='" + clientId + "')]").exists());

    mockMvc
        .perform(get("/api/v1/activities").header("Authorization", superadmin.bearer()))
        .andExpect(status().isOk());
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
            new UpdateClientApiRequest(
                "Updated",
                "Name",
                "updated@brika.test",
                "600000001",
                null,
                null,
                null,
                null,
                null,
                null));
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

  // BRIKKA V2 I3 — transition preconditions + authorized override.

  private boolean readsOverrideTrue(String metadataJson) {
    try {
      return metadataJson != null
          && objectMapper.readTree(metadataJson).path("override").asBoolean(false);
    } catch (Exception e) {
      return false;
    }
  }

  private void changeStatus(TestPrincipal actor, UUID caseId, String body) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/status")
                .header("Authorization", actor.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  @Test
  void bankSubmissionIsBlockedWithoutABankRequestAndAManagerCanOverrideIt() throws Exception {
    UUID companyId = companyRepository.insert("Co I3A", "Co I3A", "TC-I3A");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-i3a");
    UUID caseId = createCase(manager); // MORTGAGE — no seeded document requirements
    UUID clientId = createClient(manager, "cli-i3a");
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/clients")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CaseClientApiRequest(clientId, "HOLDER", true))))
        .andExpect(status().isOk());

    changeStatus(
        manager,
        caseId,
        objectMapper.writeValueAsString(new ChangeCaseStatusApiRequest("DOCUMENTATION", null)));
    changeStatus(
        manager,
        caseId,
        objectMapper.writeValueAsString(new ChangeCaseStatusApiRequest("ANALYSIS", null)));
    changeStatus(
        manager,
        caseId,
        objectMapper.writeValueAsString(new ChangeCaseStatusApiRequest("BANK_SEARCH", null)));

    // Gate 2 blocks: no bank request for the case.
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/status")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangeCaseStatusApiRequest("BANK_SUBMISSION", null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PRECONDITION_NO_BANK_REQUEST"));

    // MANAGER has CASE_TRANSITION_OVERRIDE (V28): forced with a reason -> allowed.
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/status")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangeCaseStatusApiRequest(
                            "BANK_SUBMISSION", "sending by phone, urgent", true))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("BANK_SUBMISSION"));

    List<AuditEvent> overrideEvents =
        auditEventRepository.findAll().stream()
            .filter(e -> caseId.equals(e.resourceId()))
            .filter(e -> "CASE_STATUS_CHANGED".equals(e.action()))
            .filter(e -> readsOverrideTrue(e.metadataJson()))
            .toList();
    assertThat(overrideEvents).hasSize(1);
  }

  @Test
  void overridingATransitionRequiresTheOverridePermission() throws Exception {
    UUID companyId = companyRepository.insert("Co I3B", "Co I3B", "TC-I3B");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-i3b");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-i3b");
    UUID caseId = createCase(manager);
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/assignments")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new CreateCaseAssignmentApiRequest(broker.user().id(), "BROKER"))))
        .andExpect(status().isOk());

    // BROKER has CASE_CHANGE_STATUS (via the assignment) but not CASE_TRANSITION_OVERRIDE.
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/status")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new ChangeCaseStatusApiRequest("DOCUMENTATION", "forcing", true))))
        .andExpect(status().isForbidden());
  }

  @Test
  void operationDetailsRoundTripThroughCreateGetAndPatch() throws Exception {
    UUID companyId = companyRepository.insert("Co DET", "Co DET", "TC-DET");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-det");

    UUID caseId = createCase(manager);
    mockMvc
        .perform(get("/api/v1/cases/" + caseId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestedAmount").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()));

    String createBody =
        objectMapper.writeValueAsString(
            new CreateCaseApiRequest(
                "MORTGAGE",
                new java.math.BigDecimal("250000.00"),
                "Refinanciación de la vivienda habitual"));
    String createdJson =
        mockMvc
            .perform(
                post("/api/v1/cases")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestedAmount").value(250000.00))
            .andExpect(jsonPath("$.description").value("Refinanciación de la vivienda habitual"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID createdId = UUID.fromString(objectMapper.readTree(createdJson).get("id").asText());

    String patchBody =
        objectMapper.writeValueAsString(
            new UpdateCaseApiRequest(
                "REFINANCE", new java.math.BigDecimal("300000.50"), "Notas editadas"));
    mockMvc
        .perform(
            patch("/api/v1/cases/" + createdId)
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(patchBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.operationType").value("REFINANCE"))
        .andExpect(jsonPath("$.requestedAmount").value(300000.50))
        .andExpect(jsonPath("$.description").value("Notas editadas"));
  }

  @Test
  void superadminCannotCreateClientPortalAccount() throws Exception {
    // Sprint 28 audit: CLIENT_PORTAL_ACCOUNT_CREATE was never granted to SUPERADMIN
    // (V11__portal_account_permission.sql, ADR-PORTAL-AUTH-001, Sprint 19 — "SUPERADMIN is
    // intentionally not granted this permission"). This is correct-by-design and predates Sprint
    // 27's GLOBAL-tenant-resolution pattern entirely: SUPERADMIN is rejected at the permission
    // check itself, before any tenant resolution is even attempted. This test locks in that
    // intentional exclusion so it is not "fixed" by mistake later.
    TestPrincipal superadmin = createUser(UserRole.SUPERADMIN, null, "superadmin-pa1");
    UUID companyId = companyRepository.insert("Co PA1", "Co PA1", "TC-PA1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-pa1");
    UUID clientId = createClient(manager, "cli-pa1");

    String body =
        objectMapper.writeValueAsString(
            new com.brika.platform.crm.web.CreatePortalAccountApiRequest(
                "ext-portal-" + UUID.randomUUID()));
    mockMvc
        .perform(
            post("/api/v1/clients/" + clientId + "/portal-account")
                .header("Authorization", superadmin.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void cancellingACaseWithAnOrdinaryCommentSucceedsOverHttp() throws Exception {
    UUID companyId = companyRepository.insert("Co CN1", "Co CN1", "TC-CN1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cn1");
    UUID caseId = createCase(manager);

    String body =
        objectMapper.writeValueAsString(
            new CancelCaseApiRequest(
                "CLIENT_REQUEST",
                "El cliente ha decidido posponer la operacion por motivos personales."));

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/cancel")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }

  @Test
  void cancellingACaseWithATooLongCommentReturnsAStructured400NotA500() throws Exception {
    // Sprint 29 (stabilization): this used to overflow case_status_history.reason (varchar(50) at
    // the time) and answer an unhandled DataIntegrityViolationException as a generic 500 — the
    // exact "Ha ocurrido un error inesperado" pattern reported by users. Column is now varchar(500)
    // (V20) and CaseService validates up front regardless.
    UUID companyId = companyRepository.insert("Co CN2", "Co CN2", "TC-CN2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-cn2");
    UUID caseId = createCase(manager);

    String body =
        objectMapper.writeValueAsString(new CancelCaseApiRequest("OTHER", "x".repeat(600)));

    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/cancel")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CANCELLATION_COMMENT_TOO_LONG"));
  }

  @Test
  void anUnmappedRouteReturns404NotA500() throws Exception {
    // Sprint 29 (stabilization): NoResourceFoundException used to fall through to the generic
    // Exception handler and answer 500 for a route that simply doesn't exist.
    TestPrincipal manager =
        createUser(
            UserRole.MANAGER,
            companyRepository.insert("Co CN3", "Co CN3", "TC-CN3"),
            "manager-cn3");

    mockMvc
        .perform(get("/api/v1/this-route-does-not-exist").header("Authorization", manager.bearer()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }
}
