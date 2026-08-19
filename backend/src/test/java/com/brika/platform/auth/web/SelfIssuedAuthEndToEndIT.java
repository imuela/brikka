package com.brika.platform.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.auth.PortalAccountCredentialService;
import com.brika.platform.auth.UserCredentialService;
import com.brika.platform.crm.ClientPortalAccountRepository;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 22 authorization Fase 2/3, the actual "does it work end-to-end" proof (§13: login working
 * alone is not enough) — deliberately, unlike every other *EndpointsIT in this codebase, {@link
 * com.brika.platform.identity.web.StubJwtDecoderConfig} is NOT imported, so the real
 * SelfIssuedJwtConfig decoders wired unconditionally through SecurityConfig are what authenticates
 * every request (Sprint 22 cierre, ADR-AUTH-001: Keycloak retired, self-issued is the only path).
 * Also proves the internal/Portal separation holds for self-issued tokens (ADR-PORTAL-AUTH-001,
 * autorización §4).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class SelfIssuedAuthEndToEndIT {

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
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private UserCredentialService userCredentialService;
  @Autowired private ClientRepository clientRepository;
  @Autowired private ClientPortalAccountRepository portalAccountRepository;
  @Autowired private PortalAccountCredentialService portalAccountCredentialService;

  @Test
  void selfIssuedInternalAccessTokenIsAcceptedByAProtectedInternalEndpoint() throws Exception {
    UUID companyId = companyRepository.insert("Co E2E1", "Co E2E1", "TC-E2E1");
    String email = "e2e-internal-" + UUID.randomUUID() + "@brika.test";
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.MANAGER, companyId, "ext-" + UUID.randomUUID(), email, "First", "Last"));
    userCredentialService.setPassword(user.id(), "Correct-Horse-E2E");

    String accessToken = loginInternal(email, "Correct-Horse-E2E");

    mockMvc
        .perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value(email))
        .andExpect(jsonPath("$.role").value("MANAGER"));
  }

  @Test
  void garbageBearerTokenIsRejectedByTheRealDecoder() throws Exception {
    mockMvc
        .perform(get("/api/v1/me").header("Authorization", "Bearer not-a-real-token"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void selfIssuedInternalTokenIsRejectedByThePortalChain() throws Exception {
    UUID companyId = companyRepository.insert("Co E2E2", "Co E2E2", "TC-E2E2");
    String email = "e2e-internal2-" + UUID.randomUUID() + "@brika.test";
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.MANAGER, companyId, "ext-" + UUID.randomUUID(), email, "First", "Last"));
    userCredentialService.setPassword(user.id(), "Correct-Horse-E2E2");

    String internalAccessToken = loginInternal(email, "Correct-Horse-E2E2");

    mockMvc
        .perform(get("/api/v1/portal/me").header("Authorization", "Bearer " + internalAccessToken))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void selfIssuedPortalAccessTokenIsAcceptedByAProtectedPortalEndpointAndRejectedInternally()
      throws Exception {
    UUID companyId = companyRepository.insert("Co E2E3", "Co E2E3", "TC-E2E3");
    UUID clientId =
        clientRepository.insert(
            companyId, "Client", "E2E3", "client-e2e3-" + UUID.randomUUID() + "@brika.test", "000");
    UUID portalAccountId =
        portalAccountRepository.insert(
            companyId, clientId, "ext-portal-" + UUID.randomUUID(), "ACTIVE");
    portalAccountCredentialService.setPassword(portalAccountId, "Correct-Horse-Portal-E2E");

    String clientEmail = clientRepository.findById(clientId).orElseThrow().email();
    String body =
        objectMapper.writeValueAsString(
            new LoginApiRequest(clientEmail, "Correct-Horse-Portal-E2E"));
    String response =
        mockMvc
            .perform(
                post("/api/v1/portal/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String portalAccessToken = objectMapper.readTree(response).get("accessToken").asText();

    mockMvc
        .perform(get("/api/v1/portal/me").header("Authorization", "Bearer " + portalAccessToken))
        .andExpect(status().isOk());

    // The Portal token must not authenticate against any internal endpoint either.
    mockMvc
        .perform(get("/api/v1/me").header("Authorization", "Bearer " + portalAccessToken))
        .andExpect(status().isUnauthorized());
  }

  private String loginInternal(String email, String password) throws Exception {
    String body = objectMapper.writeValueAsString(new LoginApiRequest(email, password));
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode node = objectMapper.readTree(response);
    return node.get("accessToken").asText();
  }
}
