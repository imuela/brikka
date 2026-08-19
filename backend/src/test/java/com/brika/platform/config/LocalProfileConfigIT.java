package com.brika.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.notification.EmailSender;
import com.brika.platform.notification.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 24 (entornos): verifica que el perfil LOCAL carga su configuración (application-local.yml)
 * — transporte de email a Mailpit vía {@link SmtpEmailSender}, seed habilitado por defecto y CORS
 * del dev server — y que el seed reproducible realmente puebla la DB de arranque.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("local")
class LocalProfileConfigIT {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("brika_test")
          .withUsername("brika_test")
          .withPassword("brika_test");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired EmailSender emailSender;
  @Autowired CompanyRepository companyRepository;

  @Value("${brika.notifications.email-transport}")
  String emailTransport;

  @Value("${brika.seed.enabled}")
  boolean seedEnabled;

  @Value("${brika.security.cors-allowed-origins}")
  String cors;

  @Test
  void localUsesSmtpTransportAgainstMailpitDefaults() {
    assertThat(emailTransport).isEqualTo("smtp");
    assertThat(emailSender).isInstanceOf(SmtpEmailSender.class);
  }

  @Test
  void localHasSeedEnabledAndLocalCors() {
    assertThat(seedEnabled).isTrue();
    assertThat(cors).contains("localhost:4200");
  }

  @Test
  void devSeedPopulatesTheCompanyOnBoot() {
    boolean found =
        companyRepository.findAll().stream().anyMatch(c -> "A00000000".equals(c.taxId()));
    assertThat(found).isTrue();
  }
}
