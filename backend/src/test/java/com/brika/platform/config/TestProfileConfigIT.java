package com.brika.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.UserRepository;
import com.brika.platform.notification.EmailSender;
import com.brika.platform.notification.RecordingEmailSender;
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
 * Sprint 24 (entornos): verifica que el perfil TEST carga su configuración (application-test.yml) —
 * transporte de email de prueba (recording) y seed deshabilitado por defecto — y que, con seed
 * apagado, la DB de un IT queda solo migrada (sin la empresa de demo).
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class TestProfileConfigIT {

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
  @Autowired UserRepository userRepository;

  @Value("${brika.notifications.email-transport}")
  String emailTransport;

  @Value("${brika.seed.enabled}")
  boolean seedEnabled;

  @Test
  void testUsesRecordingTransport() {
    assertThat(emailTransport).isEqualTo("test");
    assertThat(emailSender).isInstanceOf(RecordingEmailSender.class);
  }

  @Test
  void testHasSeedDisabledByDefaultAndEmptyDatabase() {
    assertThat(seedEnabled).isFalse();
    assertThat(companyRepository.findAll()).isEmpty();
    assertThat(userRepository.findAllByEmail("superadmin@brika.local")).isEmpty();
  }
}
