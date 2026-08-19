package com.brika.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 22 cierre, punto 5: proves {@link SmtpEmailSender} actually delivers over SMTP against a
 * real catcher — Mailpit, the same image used by the local docker-compose service — rather than
 * only asserting it calls a mock. Mirrors the existing "real infra via Testcontainers" convention
 * used for Postgres/MinIO elsewhere (e.g. {@code AiExtractionCallbackEndpointsIT}); no mocking
 * framework is used anywhere in this codebase.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class SmtpEmailSenderIT {

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

  @Autowired private EmailSender emailSender;

  private String mailpitApiBase() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
  }

  @Test
  void sendDeliversTheMessageToMailpit() throws Exception {
    assertThat(emailSender).isInstanceOf(SmtpEmailSender.class);

    String toEmail = "smtp-it-" + UUID.randomUUID() + "@brika.test";
    String subject = "Brika SMTP IT " + UUID.randomUUID();
    EmailSendResult result = emailSender.send(toEmail, subject, "Hello from SmtpEmailSenderIT");

    assertThat(result.sent()).isTrue();

    HttpClient client = HttpClient.newHttpClient();
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode messages = null;
    for (int attempt = 0; attempt < 20; attempt++) {
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(mailpitApiBase() + "/api/v1/search?query=to:" + toEmail))
              .GET()
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JsonNode body = objectMapper.readTree(response.body());
      if (body.get("messages_count").asInt() > 0) {
        messages = body.get("messages");
        break;
      }
      Thread.sleep(250);
    }

    assertThat(messages).isNotNull();
    assertThat(messages.size()).isEqualTo(1);
    assertThat(messages.get(0).get("Subject").asText()).isEqualTo(subject);
  }
}
