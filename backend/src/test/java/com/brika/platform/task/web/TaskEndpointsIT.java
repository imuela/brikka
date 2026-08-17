package com.brika.platform.task.web;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.casemgmt.web.CreateCaseApiRequest;
import com.brika.platform.casemgmt.web.CreateCaseAssignmentApiRequest;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Sprint 8: Tasks. Case-linked tasks are gated by CaseAccessService (CASE ASSIGNMENT applies);
 * caseless tasks use the tenant-only pattern (permission + tenant, no case check).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class TaskEndpointsIT {

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

  private void assignBroker(TestPrincipal manager, TestPrincipal broker, UUID caseId)
      throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateCaseAssignmentApiRequest(broker.user().id(), "BROKER"));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/assignments")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private String createTaskBody(UUID caseId, UUID assignedTo, String title) throws Exception {
    return objectMapper.writeValueAsString(
        new CreateTaskApiRequest(caseId, assignedTo, "FOLLOW_UP", title, "desc", null));
  }

  @Test
  void managerCreatesAndCompletesCaselessTask() throws Exception {
    UUID companyId = companyRepository.insert("Co T1", "Co T1", "TC-T1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-t1");

    String response =
        mockMvc
            .perform(
                post("/api/v1/tasks")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createTaskBody(null, null, "Call the client")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TODO"))
            .andExpect(jsonPath("$.caseId").value(nullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID taskId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(
            post("/api/v1/tasks/" + taskId + "/complete").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DONE"));
  }

  @Test
  void caseLinkedTaskRequiresCaseAssignmentForBroker() throws Exception {
    UUID companyId = companyRepository.insert("Co T2", "Co T2", "TC-T2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-t2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-t2");
    UUID caseId = createCase(manager);

    String response =
        mockMvc
            .perform(
                post("/api/v1/tasks")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createTaskBody(caseId, null, "Review docs")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID taskId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    // Broker not assigned to the case yet -> 403.
    mockMvc
        .perform(get("/api/v1/tasks/" + taskId).header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());

    assignBroker(manager, broker, caseId);

    mockMvc
        .perform(get("/api/v1/tasks/" + taskId).header("Authorization", broker.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Review docs"));
  }

  @Test
  void taskFromAnotherTenantIsNotFound() throws Exception {
    UUID companyA = companyRepository.insert("Co T3A", "Co T3A", "TC-T3A");
    UUID companyB = companyRepository.insert("Co T3B", "Co T3B", "TC-T3B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-t3a");
    TestPrincipal managerB = createUser(UserRole.MANAGER, companyB, "manager-t3b");

    String response =
        mockMvc
            .perform(
                post("/api/v1/tasks")
                    .header("Authorization", managerA.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createTaskBody(null, null, "Tenant A task")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID taskId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(get("/api/v1/tasks/" + taskId).header("Authorization", managerB.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void onlyManagerCanDeleteTask() throws Exception {
    UUID companyId = companyRepository.insert("Co T4", "Co T4", "TC-T4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-t4");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-t4");

    String response =
        mockMvc
            .perform(
                post("/api/v1/tasks")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createTaskBody(null, null, "Deletable task")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID taskId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(delete("/api/v1/tasks/" + taskId).header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(delete("/api/v1/tasks/" + taskId).header("Authorization", manager.bearer()))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/tasks/" + taskId).header("Authorization", manager.bearer()))
        .andExpect(status().isNotFound());
  }

  @Test
  void assigningToUserOutsideTenantIsRejected() throws Exception {
    UUID companyA = companyRepository.insert("Co T5A", "Co T5A", "TC-T5A");
    UUID companyB = companyRepository.insert("Co T5B", "Co T5B", "TC-T5B");
    TestPrincipal managerA = createUser(UserRole.MANAGER, companyA, "manager-t5a");
    TestPrincipal outsider = createUser(UserRole.BROKER, companyB, "broker-t5b");

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", managerA.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTaskBody(null, outsider.user().id(), "Bad assignment")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ASSIGNED_USER_NOT_IN_TENANT"));
  }

  @Test
  void completingCancelledTaskIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co T6", "Co T6", "TC-T6");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-t6");

    String response =
        mockMvc
            .perform(
                post("/api/v1/tasks")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createTaskBody(null, null, "Will be cancelled")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID taskId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    String updateBody =
        objectMapper.writeValueAsString(
            new UpdateTaskApiRequest("Will be cancelled", "desc", "CANCELLED", null, null));
    mockMvc
        .perform(
            patch("/api/v1/tasks/" + taskId)
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"));

    mockMvc
        .perform(
            post("/api/v1/tasks/" + taskId + "/complete").header("Authorization", manager.bearer()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TASK_ALREADY_CANCELLED"));
  }

  @Test
  void brokerListOnlyShowsCaselessAndAssignedCaseTasks() throws Exception {
    UUID companyId = companyRepository.insert("Co T7", "Co T7", "TC-T7");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-t7");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-t7");
    UUID assignedCase = createCase(manager);
    UUID unassignedCase = createCase(manager);
    assignBroker(manager, broker, assignedCase);

    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTaskBody(null, null, "Caseless")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTaskBody(assignedCase, null, "On assigned case")))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/tasks")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createTaskBody(unassignedCase, null, "On unassigned case")))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/tasks").header("Authorization", broker.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)));

    mockMvc
        .perform(get("/api/v1/tasks").header("Authorization", manager.bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(3)));
  }
}
