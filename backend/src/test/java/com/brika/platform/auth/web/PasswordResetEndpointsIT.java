package com.brika.platform.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * Sprint 22 authorization Fase 5: password-reset request/confirm infrastructure. Request always
 * responds 204 regardless of whether the email matches anything (§11); confirm enforces expiration,
 * single use, and revokes outstanding refresh tokens.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import({StubJwtDecoderConfig.class, CapturingPasswordResetNotifierConfig.class})
class PasswordResetEndpointsIT {

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
  @Autowired private UserCredentialService userCredentialService;
  @Autowired private ClientRepository clientRepository;
  @Autowired private ClientPortalAccountRepository portalAccountRepository;
  @Autowired private PortalAccountCredentialService portalAccountCredentialService;
  @Autowired private CapturingPasswordResetNotifierConfig notifierConfig;

  @BeforeEach
  void resetCapturedNotification() {
    // The notifier bean is a Spring-context-scoped singleton reused across every test method —
    // without this, an earlier test's capture leaks into a later "nothing captured" assertion.
    notifierConfig.reset();
  }

  @Test
  void requestForUnknownEmailStillReturnsNoContent() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new PasswordResetRequestApiRequest("no-such-" + UUID.randomUUID() + "@brika.test"));

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());
    assertThat(notifierConfig.lastCaptured()).isNull();
  }

  @Test
  void fullResetFlowChangesThePasswordAndIsSingleUse() throws Exception {
    UUID companyId = companyRepository.insert("Co PR1", "Co PR1", "TC-PR1");
    String email = "pwreset-" + UUID.randomUUID() + "@brika.test";
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.MANAGER, companyId, "ext-" + UUID.randomUUID(), email, "First", "Last"));
    userCredentialService.setPassword(user.id(), "Old-Password-1");

    String requestBody = objectMapper.writeValueAsString(new PasswordResetRequestApiRequest(email));
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
        .andExpect(status().isNoContent());

    String rawToken = notifierConfig.lastCaptured().rawToken();
    assertThat(rawToken).isNotBlank();

    String confirmBody =
        objectMapper.writeValueAsString(
            new PasswordResetConfirmApiRequest(rawToken, "Brand-New-Password-1"));
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
        .andExpect(status().isNoContent());

    // Old password no longer works, new one does.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new LoginApiRequest(email, "Old-Password-1"))))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginApiRequest(email, "Brand-New-Password-1"))))
        .andExpect(status().isOk());

    // The token is single-use.
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void confirmWithAnUnknownTokenIsRejected() throws Exception {
    String confirmBody =
        objectMapper.writeValueAsString(
            new PasswordResetConfirmApiRequest("not-a-real-token", "Whatever-New-1"));

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requestForAClientWithNoPortalCredentialIsASilentNoOp() throws Exception {
    UUID companyId = companyRepository.insert("Co PR2a", "Co PR2a", "TC-PR2A");
    String clientEmail = "pwreset-portal-nocred-" + UUID.randomUUID() + "@brika.test";
    UUID clientId = clientRepository.insert(companyId, "Client", "PR2a", clientEmail, "000");
    portalAccountRepository.insert(
        companyId, clientId, "ext-portal-" + UUID.randomUUID(), "ACTIVE");

    mockMvc
        .perform(
            post("/api/v1/portal/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PasswordResetRequestApiRequest(clientEmail))))
        .andExpect(status().isNoContent());

    assertThat(notifierConfig.lastCaptured()).isNull();
  }

  @Test
  void portalPasswordResetFlowWorksTheSameWayAndIsIndependentFromTheInternalOne() throws Exception {
    UUID companyId = companyRepository.insert("Co PR2", "Co PR2", "TC-PR2");
    String clientEmail = "pwreset-portal-" + UUID.randomUUID() + "@brika.test";
    UUID clientId = clientRepository.insert(companyId, "Client", "PR2", clientEmail, "000");
    UUID portalAccountId =
        portalAccountRepository.insert(
            companyId, clientId, "ext-portal-" + UUID.randomUUID(), "ACTIVE");
    portalAccountCredentialService.setPassword(portalAccountId, "Old-Portal-Password-1");

    mockMvc
        .perform(
            post("/api/v1/portal/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PasswordResetRequestApiRequest(clientEmail))))
        .andExpect(status().isNoContent());

    String rawToken = notifierConfig.lastCaptured().rawToken();
    assertThat(rawToken).isNotBlank();

    mockMvc
        .perform(
            post("/api/v1/portal/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PasswordResetConfirmApiRequest(
                            rawToken, "Brand-New-Portal-Password-1"))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/portal/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginApiRequest(clientEmail, "Brand-New-Portal-Password-1"))))
        .andExpect(status().isOk());

    // The internal token space is untouched — this token must not confirm anything there.
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new PasswordResetConfirmApiRequest(rawToken, "Should-Not-Apply-1"))))
        .andExpect(status().isUnauthorized());
  }
}
