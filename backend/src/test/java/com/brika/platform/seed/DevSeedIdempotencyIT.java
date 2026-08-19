package com.brika.platform.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.brika.platform.auth.UserCredentialService;
import com.brika.platform.bank.BankRepository;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 24 (seed reproducible): con el perfil test + {@code brika.seed.enabled=true}, {@link
 * DevSeedRunner} debe ser idempotente — ejecutarlo más de una vez sobre la misma DB no duplica
 * empresa, usuarios ni bancos — y no debe pisar una contraseña que un operador haya cambiado entre
 * arranques. Cada método arranca de una DB limpia y sembrada una vez (wipe + seed en
 * {@code @BeforeEach}) para no interferir entre tests.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class DevSeedIdempotencyIT {

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
    registry.add("brika.seed.enabled", () -> "true");
  }

  @Autowired DevSeedRunner seedRunner;
  @Autowired CompanyRepository companyRepository;
  @Autowired UserRepository userRepository;
  @Autowired BankRepository bankRepository;
  @Autowired UserCredentialService userCredentialService;
  @Autowired JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanAndSeedOnce() throws Exception {
    jdbcTemplate.update("DELETE FROM user_roles");
    jdbcTemplate.update("DELETE FROM users");
    jdbcTemplate.update("DELETE FROM banks");
    jdbcTemplate.update("DELETE FROM companies");
    seedRunner.run();
  }

  @Test
  void runningAgainDoesNotDuplicateEntities() throws Exception {
    int companies = companyRepository.findAll().size();
    int superadmins = userRepository.findAllByEmail("superadmin@brika.local").size();
    int banks = bankRepository.findAll().size();

    seedRunner.run();
    seedRunner.run();

    assertThat(companyRepository.findAll().size()).isEqualTo(companies);
    assertThat(userRepository.findAllByEmail("superadmin@brika.local").size())
        .isEqualTo(superadmins);
    assertThat(bankRepository.findAll().size()).isEqualTo(banks);
  }

  @Test
  void seedDoesNotOverwriteAnExistingChangedPassword() throws Exception {
    User manager = userRepository.findAllByEmail("manager@brika.local").get(0);

    String customPassword = "a-different-dev-password";
    userCredentialService.setPassword(manager.id(), customPassword);

    // A second boot/run of the seed must leave the custom password untouched.
    seedRunner.run();

    assertThat(userCredentialService.verify(manager.id(), customPassword)).isTrue();
    assertThat(userCredentialService.verify(manager.id(), "brika_dev_password")).isFalse();
  }

  @Test
  void seedsCompanyUsersAndBanks() {
    assertThat(companyRepository.findAll()).anyMatch(c -> "A00000000".equals(c.taxId()));

    List<User> users =
        List.of(
            userRepository.findAllByEmail("superadmin@brika.local").get(0),
            userRepository.findAllByEmail("manager@brika.local").get(0),
            userRepository.findAllByEmail("broker@brika.local").get(0));
    assertThat(users).hasSize(3);
    for (User user : users) {
      assertThat(userCredentialService.verify(user.id(), "brika_dev_password")).isTrue();
    }

    assertThat(bankRepository.findAll())
        .extracting(b -> b.code())
        .contains("BANCO_SANTANDER", "BANCO_BBVA", "BANCO_CAIXABANK");
  }
}
