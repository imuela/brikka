package com.brika.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Sprint 22 Fase 1 (autorización, GATE 1): credenciales Argon2id, rotación/reutilización de refresh
 * tokens, y bloqueo por intentos fallidos — la parte de los fundamentos que no depende todavía de
 * la decisión de mapeo de identidad pendiente (emisor/decoder JWT propio).
 */
@Testcontainers
@SpringBootTest
class SelfAuthFoundationsIT {

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

  @Autowired private DataSource dataSource;
  @Autowired private UserCredentialService userCredentialService;
  @Autowired private PortalAccountCredentialService portalAccountCredentialService;
  @Autowired private UserRefreshTokenService userRefreshTokenService;
  @Autowired private PortalRefreshTokenService portalRefreshTokenService;
  @Autowired private LoginAttemptService loginAttemptService;

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private UUID userId;
  private UUID portalAccountId;

  @BeforeEach
  void seedFixtures() {
    UUID companyId =
        jdbc()
            .queryForObject(
                "INSERT INTO companies (legal_name, trade_name, tax_id, status) VALUES"
                    + " ('Test SL', 'Test', 'X0000000X', 'ACTIVE') RETURNING id",
                UUID.class);
    userId =
        jdbc()
            .queryForObject(
                "INSERT INTO users (company_id, external_identity_id, email, first_name,"
                    + " last_name, status) VALUES (?, ?, ?, 'Test', 'User', 'ACTIVE') RETURNING"
                    + " id",
                UUID.class,
                companyId,
                UUID.randomUUID().toString(),
                "fase1-" + UUID.randomUUID() + "@brika.test");
    UUID clientId =
        jdbc()
            .queryForObject(
                "INSERT INTO clients (company_id, first_name, last_name, email, phone, status)"
                    + " VALUES (?, 'Test', 'Client', ?, '000000000', 'ACTIVE') RETURNING id",
                UUID.class,
                companyId,
                "client-" + UUID.randomUUID() + "@brika.test");
    portalAccountId =
        jdbc()
            .queryForObject(
                "INSERT INTO client_portal_accounts (company_id, client_id, external_identity_id,"
                    + " status) VALUES (?, ?, ?, 'ACTIVE') RETURNING id",
                UUID.class,
                companyId,
                clientId,
                UUID.randomUUID().toString());
  }

  @Test
  void userCredentialRoundTripVerifiesCorrectAndRejectsWrongPassword() {
    assertThat(userCredentialService.hasCredential(userId)).isFalse();

    userCredentialService.setPassword(userId, "correct-horse-battery-staple");

    assertThat(userCredentialService.hasCredential(userId)).isTrue();
    assertThat(userCredentialService.verify(userId, "correct-horse-battery-staple")).isTrue();
    assertThat(userCredentialService.verify(userId, "wrong-password")).isFalse();
  }

  @Test
  void verifyAgainstUnknownUserIdIsFalseNotAnException() {
    assertThat(userCredentialService.verify(UUID.randomUUID(), "anything")).isFalse();
  }

  @Test
  void passwordHashIsNeverStoredInPlainText() {
    userCredentialService.setPassword(userId, "correct-horse-battery-staple");
    String storedHash =
        jdbc()
            .queryForObject(
                "SELECT password_hash FROM user_credentials WHERE user_id = ?",
                String.class,
                userId);
    assertThat(storedHash).doesNotContain("correct-horse-battery-staple");
    assertThat(storedHash).startsWith("$argon2id$");
  }

  @Test
  void portalAccountCredentialRoundTrip() {
    portalAccountCredentialService.setPassword(portalAccountId, "client-secret-pw");

    assertThat(portalAccountCredentialService.verify(portalAccountId, "client-secret-pw")).isTrue();
    assertThat(portalAccountCredentialService.verify(portalAccountId, "not-it")).isFalse();
  }

  @Test
  void refreshTokenRotationIssuesNewTokenAndRevokesThePreviousOne() {
    IssuedRefreshToken first = userRefreshTokenService.issue(userId);

    IssuedRefreshToken second = userRefreshTokenService.rotate(first.rawToken());

    assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
    assertThat(second.familyId()).isEqualTo(first.familyId());
    // The rotated (second) token must still work for a further rotation.
    IssuedRefreshToken third = userRefreshTokenService.rotate(second.rawToken());
    assertThat(third.rawToken()).isNotEqualTo(second.rawToken());
  }

  @Test
  void reusingAnAlreadyRotatedRefreshTokenIsDetectedAndRevokesTheWholeFamily() {
    IssuedRefreshToken first = userRefreshTokenService.issue(userId);
    IssuedRefreshToken second = userRefreshTokenService.rotate(first.rawToken());

    // Reusing the already-rotated-away first token is a theft signal.
    assertThatThrownBy(() -> userRefreshTokenService.rotate(first.rawToken()))
        .isInstanceOf(InvalidRefreshTokenException.class)
        .extracting(e -> ((InvalidRefreshTokenException) e).reason())
        .isEqualTo(InvalidRefreshTokenException.Reason.REUSED);

    // The reuse must have revoked the entire family, including the second (legitimate) token.
    assertThatThrownBy(() -> userRefreshTokenService.rotate(second.rawToken()))
        .isInstanceOf(InvalidRefreshTokenException.class)
        .extracting(e -> ((InvalidRefreshTokenException) e).reason())
        .isEqualTo(InvalidRefreshTokenException.Reason.REUSED);
  }

  @Test
  void unknownRefreshTokenIsRejected() {
    assertThatThrownBy(() -> userRefreshTokenService.rotate("not-a-real-token"))
        .isInstanceOf(InvalidRefreshTokenException.class)
        .extracting(e -> ((InvalidRefreshTokenException) e).reason())
        .isEqualTo(InvalidRefreshTokenException.Reason.UNKNOWN);
  }

  @Test
  void revokeAllForUserInvalidatesAllOutstandingRefreshTokens() {
    IssuedRefreshToken token = userRefreshTokenService.issue(userId);

    userRefreshTokenService.revokeAllForUser(userId);

    assertThatThrownBy(() -> userRefreshTokenService.rotate(token.rawToken()))
        .isInstanceOf(InvalidRefreshTokenException.class);
  }

  @Test
  void portalRefreshTokenRotationAndReuseDetectionMirrorsTheInternalFlow() {
    IssuedRefreshToken first = portalRefreshTokenService.issue(portalAccountId);
    IssuedRefreshToken second = portalRefreshTokenService.rotate(first.rawToken());

    assertThat(second.rawToken()).isNotEqualTo(first.rawToken());
    assertThatThrownBy(() -> portalRefreshTokenService.rotate(first.rawToken()))
        .isInstanceOf(InvalidRefreshTokenException.class)
        .extracting(e -> ((InvalidRefreshTokenException) e).reason())
        .isEqualTo(InvalidRefreshTokenException.Reason.REUSED);
  }

  @Test
  void internalAndPortalRefreshTokensAreCompletelyIndependentTables() {
    IssuedRefreshToken internal = userRefreshTokenService.issue(userId);

    // A token minted for the internal realm must not be a valid Portal refresh token, and
    // vice versa — the two services only ever look at their own table.
    assertThatThrownBy(() -> portalRefreshTokenService.rotate(internal.rawToken()))
        .isInstanceOf(InvalidRefreshTokenException.class)
        .extracting(e -> ((InvalidRefreshTokenException) e).reason())
        .isEqualTo(InvalidRefreshTokenException.Reason.UNKNOWN);
  }

  @Test
  void loginAttemptServiceLocksAfterTheConfiguredNumberOfFailures() {
    String identifier = "lockout-" + UUID.randomUUID() + "@brika.test";

    assertThat(loginAttemptService.isLocked(LoginAttemptService.REALM_INTERNAL, identifier))
        .isFalse();

    for (int i = 0; i < 5; i++) {
      loginAttemptService.recordFailure(LoginAttemptService.REALM_INTERNAL, identifier);
    }

    assertThat(loginAttemptService.isLocked(LoginAttemptService.REALM_INTERNAL, identifier))
        .isTrue();
  }

  @Test
  void loginAttemptLockoutIsScopedPerRealm() {
    String identifier = "cross-realm-" + UUID.randomUUID() + "@brika.test";
    for (int i = 0; i < 5; i++) {
      loginAttemptService.recordFailure(LoginAttemptService.REALM_INTERNAL, identifier);
    }

    assertThat(loginAttemptService.isLocked(LoginAttemptService.REALM_INTERNAL, identifier))
        .isTrue();
    assertThat(loginAttemptService.isLocked(LoginAttemptService.REALM_PORTAL, identifier))
        .isFalse();
  }
}
