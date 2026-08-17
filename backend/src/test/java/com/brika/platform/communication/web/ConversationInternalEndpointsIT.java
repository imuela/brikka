package com.brika.platform.communication.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Sprint 8: INTERNAL conversations. ADR-COMMS-002: authorization stays implicit via CASE
 * ASSIGNMENT, no conversation_participants row. CLIENT-type behavior (Sprint 7) is exercised
 * elsewhere (PortalEndpointsIT) and is not repeated here.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class ConversationInternalEndpointsIT {

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

  @Test
  void managerCreatesInternalConversationAndBrokerCanMessage() throws Exception {
    UUID companyId = companyRepository.insert("Co C1", "Co C1", "TC-C1");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-c1");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-c1");
    UUID caseId = createCase(manager);
    assignBroker(manager, broker, caseId);

    String body =
        objectMapper.writeValueAsString(new CreateConversationApiRequest("INTERNAL", null));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/conversations")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("INTERNAL"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID conversationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    String messageBody = objectMapper.writeValueAsString(new CreateMessageApiRequest("hello team"));
    mockMvc
        .perform(
            post("/api/v1/conversations/" + conversationId + "/messages")
                .header("Authorization", broker.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(messageBody))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.body").value("hello team"));

    mockMvc
        .perform(
            get("/api/v1/conversations/" + conversationId + "/messages")
                .header("Authorization", manager.bearer()))
        .andExpect(status().isOk());
  }

  @Test
  void brokerWithoutCaseAssignmentCannotAccessInternalConversation() throws Exception {
    UUID companyId = companyRepository.insert("Co C2", "Co C2", "TC-C2");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-c2");
    TestPrincipal broker = createUser(UserRole.BROKER, companyId, "broker-c2");
    UUID caseId = createCase(manager); // broker never assigned

    String body =
        objectMapper.writeValueAsString(new CreateConversationApiRequest("INTERNAL", null));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/conversations")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID conversationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    mockMvc
        .perform(
            get("/api/v1/conversations/" + conversationId + "/messages")
                .header("Authorization", broker.bearer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void addingParticipantToInternalConversationIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co C3", "Co C3", "TC-C3");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-c3");
    UUID caseId = createCase(manager);

    String body =
        objectMapper.writeValueAsString(new CreateConversationApiRequest("INTERNAL", null));
    String response =
        mockMvc
            .perform(
                post("/api/v1/cases/" + caseId + "/conversations")
                    .header("Authorization", manager.bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID conversationId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

    String participantBody =
        objectMapper.writeValueAsString(
            new AddConversationParticipantApiRequest(UUID.randomUUID()));
    mockMvc
        .perform(
            post("/api/v1/conversations/" + conversationId + "/participants")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(participantBody))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PARTICIPANTS_NOT_SUPPORTED_FOR_TYPE"));
  }

  @Test
  void invalidConversationTypeIsRejected() throws Exception {
    UUID companyId = companyRepository.insert("Co C4", "Co C4", "TC-C4");
    TestPrincipal manager = createUser(UserRole.MANAGER, companyId, "manager-c4");
    UUID caseId = createCase(manager);

    String body =
        objectMapper.writeValueAsString(new CreateConversationApiRequest("SYSTEM", List.of()));
    mockMvc
        .perform(
            post("/api/v1/cases/" + caseId + "/conversations")
                .header("Authorization", manager.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_CONVERSATION_TYPE"));
  }
}
