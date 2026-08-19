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
 * Sprint 22 cierre, punto 4: {@code /internal/auth/**} shared-secret bootstrap endpoint used to set
 * an initial/migrated credential without a self-service flow — same manual-secret, fail-closed
 * pattern as {@code AiExtractionCallbackController} (ADR-AI-001). Never a product feature: only
 * used to bootstrap/migrate local credentials outside a real authenticated session.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class InternalCredentialBootstrapControllerIT {

  private static final String SECRET = "test-internal-bootstrap-secret";

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
    registry.add("brika.security.self-auth.internal-bootstrap-secret", () -> SECRET);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CompanyRepository companyRepository;
  @Autowired private UserProvisioningService userProvisioningService;
  @Autowired private UserCredentialService userCredentialService;
  @Autowired private ClientRepository clientRepository;
  @Autowired private ClientPortalAccountRepository portalAccountRepository;
  @Autowired private PortalAccountCredentialService portalAccountCredentialService;

  private UUID createUser(String emailPrefix) {
    UUID companyId = companyRepository.insert("Co ICB", "Co ICB", "TC-ICB-" + UUID.randomUUID());
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.MANAGER,
                companyId,
                "ext-" + UUID.randomUUID(),
                emailPrefix + "-" + UUID.randomUUID() + "@brika.test",
                "First",
                "Last"));
    return user.id();
  }

  private UUID createPortalAccount(String emailPrefix) {
    UUID companyId = companyRepository.insert("Co ICB", "Co ICB", "TC-ICB-" + UUID.randomUUID());
    UUID clientId =
        clientRepository.insert(
            companyId,
            "Client",
            "ICB",
            emailPrefix + "-" + UUID.randomUUID() + "@brika.test",
            "000");
    return portalAccountRepository.insert(
        companyId, clientId, "ext-portal-" + UUID.randomUUID(), "ACTIVE");
  }

  @Test
  void setUserPasswordWithCorrectSecretUpdatesTheCredential() throws Exception {
    UUID userId = createUser("bootstrap-user");
    String body = objectMapper.writeValueAsString(new SetPasswordApiRequest("Bootstrap-Pass-1"));

    mockMvc
        .perform(
            post("/internal/auth/users/" + userId + "/credentials")
                .header("X-Internal-Auth-Secret", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    assertThat(userCredentialService.verify(userId, "Bootstrap-Pass-1")).isTrue();
  }

  @Test
  void setUserPasswordWithWrongSecretIsRejectedAndCredentialIsUnchanged() throws Exception {
    UUID userId = createUser("bootstrap-user-wrong");
    userCredentialService.setPassword(userId, "Original-Pass-1");
    String body = objectMapper.writeValueAsString(new SetPasswordApiRequest("Should-Not-Apply-1"));

    mockMvc
        .perform(
            post("/internal/auth/users/" + userId + "/credentials")
                .header("X-Internal-Auth-Secret", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertThat(userCredentialService.verify(userId, "Original-Pass-1")).isTrue();
    assertThat(userCredentialService.verify(userId, "Should-Not-Apply-1")).isFalse();
  }

  @Test
  void setUserPasswordWithoutSecretHeaderIsRejected() throws Exception {
    UUID userId = createUser("bootstrap-user-nohdr");
    String body = objectMapper.writeValueAsString(new SetPasswordApiRequest("Whatever-Pass-1"));

    mockMvc
        .perform(
            post("/internal/auth/users/" + userId + "/credentials")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }

  @Test
  void setPortalAccountPasswordWithCorrectSecretUpdatesTheCredential() throws Exception {
    UUID portalAccountId = createPortalAccount("bootstrap-portal");
    String body =
        objectMapper.writeValueAsString(new SetPasswordApiRequest("Bootstrap-Portal-Pass-1"));

    mockMvc
        .perform(
            post("/internal/auth/portal-accounts/" + portalAccountId + "/credentials")
                .header("X-Internal-Auth-Secret", SECRET)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());

    assertThat(portalAccountCredentialService.verify(portalAccountId, "Bootstrap-Portal-Pass-1"))
        .isTrue();
  }

  @Test
  void setPortalAccountPasswordWithWrongSecretIsRejected() throws Exception {
    UUID portalAccountId = createPortalAccount("bootstrap-portal-wrong");
    String body = objectMapper.writeValueAsString(new SetPasswordApiRequest("Should-Not-Apply-1"));

    mockMvc
        .perform(
            post("/internal/auth/portal-accounts/" + portalAccountId + "/credentials")
                .header("X-Internal-Auth-Secret", "wrong-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());

    assertThat(portalAccountCredentialService.verify(portalAccountId, "Should-Not-Apply-1"))
        .isFalse();
  }
}
