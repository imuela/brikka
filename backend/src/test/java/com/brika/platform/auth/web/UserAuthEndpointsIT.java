package com.brika.platform.auth.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.brika.platform.auth.UserCredentialService;
import com.brika.platform.identity.CompanyRepository;
import com.brika.platform.identity.CreateUserCommand;
import com.brika.platform.identity.User;
import com.brika.platform.identity.UserProvisioningService;
import com.brika.platform.identity.UserRepository;
import com.brika.platform.identity.UserRole;
import com.brika.platform.identity.web.StubJwtDecoderConfig;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Sprint 22 authorization Fase 2, GATE 2: login/refresh/logout/change-password for internal users,
 * plus the security scenarios §12 explicitly requires (wrong password, unknown user, disabled user,
 * refresh reuse detection, lockout). Uses StubJwtDecoderConfig only for the already-authenticated
 * change-password call — login/refresh/logout are permitAll and never touch a JwtDecoder at all.
 * The end-to-end proof that a token minted here is accepted by the real self-issued decoder lives
 * in {@link SelfIssuedAuthEndToEndIT}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubJwtDecoderConfig.class)
class UserAuthEndpointsIT {

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
  @Autowired private UserRepository userRepository;

  private record Fixture(User user, String externalIdentityId, String email) {
    String bearer() {
      return "Bearer " + externalIdentityId;
    }
  }

  private Fixture seedUser(String emailPrefix, String password) {
    UUID companyId =
        companyRepository.insert("Co " + emailPrefix, "Co " + emailPrefix, "TC-" + emailPrefix);
    String externalId = "ext-" + UUID.randomUUID();
    String email = emailPrefix + "@brika.test";
    User user =
        userProvisioningService.createUser(
            new CreateUserCommand(UserRole.MANAGER, companyId, externalId, email, "First", "Last"));
    userCredentialService.setPassword(user.id(), password);
    return new Fixture(user, externalId, email);
  }

  private JsonNode login(String email, String password) throws Exception {
    String body = objectMapper.writeValueAsString(new LoginApiRequest(email, password));
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response);
  }

  @Test
  void loginWithCorrectCredentialsIssuesAccessAndRefreshTokens() throws Exception {
    Fixture fixture = seedUser("login-ok", "Correct-Horse-1");

    JsonNode result = login(fixture.email(), "Correct-Horse-1");

    org.assertj.core.api.Assertions.assertThat(result.get("accessToken").asText()).isNotBlank();
    org.assertj.core.api.Assertions.assertThat(result.get("refreshToken").asText()).isNotBlank();
    org.assertj.core.api.Assertions.assertThat(result.get("expiresInSeconds").asLong())
        .isPositive();
  }

  @Test
  void loginWithWrongPasswordIsRejected() throws Exception {
    Fixture fixture = seedUser("login-wrong", "Correct-Horse-2");
    String body =
        objectMapper.writeValueAsString(new LoginApiRequest(fixture.email(), "totally-wrong"));

    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginWithUnknownEmailIsRejectedWithTheSameStatusAsWrongPassword() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new LoginApiRequest("no-such-user-" + UUID.randomUUID() + "@brika.test", "whatever"));

    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginForADisabledUserIsRejected() throws Exception {
    Fixture fixture = seedUser("login-disabled", "Correct-Horse-3");
    userRepository.disable(fixture.user().id());
    String body =
        objectMapper.writeValueAsString(new LoginApiRequest(fixture.email(), "Correct-Horse-3"));

    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginForAReenabledUserSucceedsAgain() throws Exception {
    // Sprint 37 (D36-4): the symmetric case of loginForADisabledUserIsRejected — a disabled user
    // reactivated via UserController.enable() must be able to authenticate again, exercising the
    // real login path against the real users.status column, not just the repository state.
    Fixture fixture = seedUser("login-reenabled", "Correct-Horse-3b");
    userRepository.disable(fixture.user().id());
    userRepository.enable(fixture.user().id());
    String body =
        objectMapper.writeValueAsString(new LoginApiRequest(fixture.email(), "Correct-Horse-3b"));

    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
  }

  @Test
  void refreshRotatesTheTokenAndInvalidatesThePreviousOne() throws Exception {
    Fixture fixture = seedUser("refresh-ok", "Correct-Horse-4");
    JsonNode loginResult = login(fixture.email(), "Correct-Horse-4");
    String firstRefreshToken = loginResult.get("refreshToken").asText();

    String refreshBody = objectMapper.writeValueAsString(new RefreshApiRequest(firstRefreshToken));
    String refreshResponse =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode refreshed = objectMapper.readTree(refreshResponse);
    org.assertj.core.api.Assertions.assertThat(refreshed.get("refreshToken").asText())
        .isNotEqualTo(firstRefreshToken);

    // Reusing the original (now rotated-away) refresh token must fail — reuse detection.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody))
        .andExpect(status().isUnauthorized());

    // Reuse detection must revoke the entire family, not just the token that was replayed — the
    // still-active sibling token minted by the rotation above must stop working too (token-theft
    // signal: whoever holds the old token might also hold the new one).
    String currentRefreshToken = refreshed.get("refreshToken").asText();
    String currentRefreshBody =
        objectMapper.writeValueAsString(new RefreshApiRequest(currentRefreshToken));
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(currentRefreshBody))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutInvalidatesTheRefreshToken() throws Exception {
    Fixture fixture = seedUser("logout-ok", "Correct-Horse-5");
    JsonNode loginResult = login(fixture.email(), "Correct-Horse-5");
    String refreshToken = loginResult.get("refreshToken").asText();

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LogoutApiRequest(refreshToken))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshApiRequest(refreshToken))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void logoutWithMissingBodyReturnsBadRequestNotInternalError() throws Exception {
    // Sprint 40: reproduces the documented bug — a missing @RequestBody used to fall through
    // GlobalExceptionHandler's generic catch-all as a 500 INTERNAL_ERROR. Real endpoint, no mock.
    // logout is permitAll (see class Javadoc), so no Authorization header is needed here either.
    mockMvc
        .perform(post("/api/v1/auth/logout"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void loginWithMalformedJsonReturnsBadRequestNotInternalError() throws Exception {
    mockMvc
        .perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void changePasswordRequiresCurrentPasswordAndRevokesExistingRefreshTokens() throws Exception {
    Fixture fixture = seedUser("chpwd-ok", "Correct-Horse-6");
    JsonNode loginResult = login(fixture.email(), "Correct-Horse-6");
    String oldRefreshToken = loginResult.get("refreshToken").asText();

    String wrongCurrentBody =
        objectMapper.writeValueAsString(
            new ChangePasswordApiRequest("not-the-current-password", "New-Horse-6"));
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header("Authorization", fixture.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(wrongCurrentBody))
        .andExpect(status().isUnauthorized());

    String body =
        objectMapper.writeValueAsString(
            new ChangePasswordApiRequest("Correct-Horse-6", "New-Horse-6"));
    mockMvc
        .perform(
            post("/api/v1/auth/change-password")
                .header("Authorization", fixture.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isNoContent());

    // Old password no longer works.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new LoginApiRequest(fixture.email(), "Correct-Horse-6"))))
        .andExpect(status().isUnauthorized());

    // New password works.
    login(fixture.email(), "New-Horse-6");

    // The refresh token issued before the password change is now revoked.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshApiRequest(oldRefreshToken))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void loginIsLockedOutAfterTooManyFailedAttempts() throws Exception {
    Fixture fixture = seedUser("lockout", "Correct-Horse-7");
    String wrongBody =
        objectMapper.writeValueAsString(new LoginApiRequest(fixture.email(), "wrong-password"));

    for (int i = 0; i < 5; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(wrongBody))
          .andExpect(status().isUnauthorized());
    }

    // Even the correct password is now rejected — locked out, not just "still wrong".
    String correctBody =
        objectMapper.writeValueAsString(new LoginApiRequest(fixture.email(), "Correct-Horse-7"));
    mockMvc
        .perform(
            post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(correctBody))
        .andExpect(status().isTooManyRequests());
  }
}
