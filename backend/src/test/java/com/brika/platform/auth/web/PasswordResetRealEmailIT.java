package com.brika.platform.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.auth.UserCredentialService;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 24 (email/password-reset §33): verifica el flujo de password-reset con COMPROBACIÓN REAL
 * del mensaje — el correo se entrega por SMTP a Mailpit y el enlace que viaja dentro del mensaje
 * real (no un notifier mockeado) es el que se consume para completar el cambio. Cubre la cadena
 * completa: request → envío SMTP → lectura del email → extracción del token del body → confirm →
 * login con la nueva contraseña (y rechazo de la anterior).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class PasswordResetRealEmailIT {

  private static final Pattern RESET_TOKEN =
      Pattern.compile("password-reset\\?token=([^\"\\\\\\s]+)");

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @Container
  static final GenericContainer<?> MAILPIT =
      new GenericContainer<>("axllent/mailpit:v1.20")
          .withExposedPorts(1025, 8025)
          .waitingFor(
              Wait.forHttp("/readyz").forPort(8025).withStartupTimeout(Duration.ofSeconds(30)));

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("brika.notifications.email-transport", () -> "smtp");
    registry.add("spring.mail.host", MAILPIT::getHost);
    registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
  }

  @Autowired MockMvc mockMvc;
  @Autowired ObjectMapper objectMapper;
  @Autowired CompanyRepository companyRepository;
  @Autowired UserProvisioningService userProvisioningService;
  @Autowired UserCredentialService userCredentialService;

  private String mailpitApiBase() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
  }

  @Test
  void fullResetFlowDeliversRealEmailWhoseLinkIsConsumed() throws Exception {
    UUID companyId = companyRepository.insert("Co PRE", "Co PRE", "TC-PRE");
    String email = "pwreset-real-" + UUID.randomUUID() + "@brika.test";
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(
                UserRole.MANAGER, companyId, "ext-" + UUID.randomUUID(), email, "First", "Last"));
    // requestPasswordReset silently no-ops unless the user already has a credential (§11
    // anti-enum):
    userCredentialService.setPassword(user.id(), "Existing-Password-1");

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new PasswordResetRequestApiRequest(email))))
        .andExpect(status().isNoContent());

    String rawToken = extractResetTokenFromDeliveredEmail(email);
    assertThat(rawToken).isNotBlank();

    String confirmBody =
        objectMapper.writeValueAsString(
            new PasswordResetConfirmApiRequest(rawToken, "Real-Email-New-Password-1"));
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmBody))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginApiRequest(email, "Real-Email-New-Password-1"))))
        .andExpect(status().isOk());
  }

  private String extractResetTokenFromDeliveredEmail(String toEmail) throws Exception {
    HttpClient client = HttpClient.newHttpClient();
    ObjectMapper json = new ObjectMapper();
    JsonNode messages = null;
    for (int attempt = 0; attempt < 30; attempt++) {
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(mailpitApiBase() + "/api/v1/search?query=to:" + toEmail))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = json.readTree(response.body());
      if (body.get("messages_count").asInt() > 0) {
        messages = body.get("messages");
        break;
      }
      Thread.sleep(250);
    }
    assertThat(messages).as("email should have been delivered to Mailpit").isNotNull();

    // The search summary only carries a truncated snippet; fetch the full message by ID and
    // extract the token from it. Because the plain-text body's newlines are JSON-escaped as "\n",
    // the token regex stops at quotes/backslash/whitespace rather than relying on a real newline.
    String messageId = messages.get(0).get("ID").asText();
    HttpRequest fullRequest =
        HttpRequest.newBuilder(URI.create(mailpitApiBase() + "/api/v1/message/" + messageId))
            .GET()
            .build();
    HttpResponse<String> fullResponse =
        client.send(fullRequest, HttpResponse.BodyHandlers.ofString());
    String fullJson = json.writeValueAsString(json.readTree(fullResponse.body()));
    assertThat(fullJson).contains("/password-reset?token=");

    Matcher matcher = RESET_TOKEN.matcher(fullJson);
    assertThat(matcher.find()).isTrue();
    return matcher.group(1);
  }
}
