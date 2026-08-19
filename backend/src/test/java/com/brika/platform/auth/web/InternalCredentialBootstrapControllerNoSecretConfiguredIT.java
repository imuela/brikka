package com.brika.platform.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * Separate context (no {@code brika.security.self-auth.internal-bootstrap-secret} override):
 * exercises the fail-closed default from {@code application.yml} — an unset secret must reject
 * every request rather than accept an unauthenticated one, exactly like {@code
 * brika.ai.worker-callback-secret} (ADR-AI-001).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class InternalCredentialBootstrapControllerNoSecretConfiguredIT {

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

  @Test
  void anyRequestIsRejectedWhenNoSecretIsConfigured() throws Exception {
    UUID companyId =
        companyRepository.insert("Co ICBNS", "Co ICBNS", "TC-ICBNS-" + UUID.randomUUID());
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.MANAGER,
                companyId,
                "ext-" + UUID.randomUUID(),
                "bootstrap-nosecret-" + UUID.randomUUID() + "@brika.test",
                "First",
                "Last"));
    String body = objectMapper.writeValueAsString(new SetPasswordApiRequest("Whatever-Pass-1"));

    mockMvc
        .perform(
            post("/internal/auth/users/" + user.id() + "/credentials")
                .header("X-Internal-Auth-Secret", "any-guess")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isForbidden());
  }
}
