package com.brika.platform.auth;

import com.brika.platform.identity.User;
import com.brika.platform.identity.UserRepository;
import com.brika.platform.security.OpaqueTokenGenerator;
import com.brika.platform.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates login/refresh/logout/change-password/password-reset for internal users
 * (SUPERADMIN/MANAGER/BROKER), wiring together the Fase 1 building blocks. Deliberately separate
 * from the Portal equivalent (ADR-PORTAL-AUTH-001) — see {@link UserAccessTokenIssuer}'s javadoc.
 */
@Service
public class UserAuthenticationService {

  private final UserRepository userRepository;
  private final UserCredentialService credentialService;
  private final UserRefreshTokenService refreshTokenService;
  private final UserAccessTokenIssuer accessTokenIssuer;
  private final LoginAttemptService loginAttemptService;
  private final UserPasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordResetNotifier passwordResetNotifier;
  private final long accessTokenTtlSeconds;
  private final Duration passwordResetTtl;

  public UserAuthenticationService(
      UserRepository userRepository,
      UserCredentialService credentialService,
      UserRefreshTokenService refreshTokenService,
      UserAccessTokenIssuer accessTokenIssuer,
      LoginAttemptService loginAttemptService,
      UserPasswordResetTokenRepository passwordResetTokenRepository,
      @Qualifier("userPasswordResetNotifier") PasswordResetNotifier passwordResetNotifier,
      @Value("${brika.security.self-auth.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
      @Value("${brika.security.self-auth.password-reset-ttl-seconds:3600}")
          long passwordResetTtlSeconds) {
    this.userRepository = userRepository;
    this.credentialService = credentialService;
    this.refreshTokenService = refreshTokenService;
    this.accessTokenIssuer = accessTokenIssuer;
    this.loginAttemptService = loginAttemptService;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.passwordResetNotifier = passwordResetNotifier;
    this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    this.passwordResetTtl = Duration.ofSeconds(passwordResetTtlSeconds);
  }

  public AccessTokenResult login(String email, String rawPassword) {
    String normalizedEmail = email == null ? "" : email.strip().toLowerCase();
    if (loginAttemptService.isLocked(LoginAttemptService.REALM_INTERNAL, normalizedEmail)) {
      throw new TooManyLoginAttemptsException();
    }

    List<User> matches = userRepository.findAllByEmail(normalizedEmail);
    // Exactly one match, ACTIVE, required. More than one is the ADR-IDENTITY-001 cross-company
    // email collision case (Sprint 22 authorization decision: reject as a generic failure, not a
    // silent pick). Zero, more-than-one, and disabled all take the same path deliberately, so a
    // caller can never learn which case it was — including via response timing, hence the
    // placeholder verify() call.
    if (matches.size() != 1 || !"ACTIVE".equals(matches.get(0).status())) {
      credentialService.verify(UUID.randomUUID(), rawPassword);
      loginAttemptService.recordFailure(LoginAttemptService.REALM_INTERNAL, normalizedEmail);
      throw new AuthenticationFailedException();
    }

    User user = matches.get(0);
    if (!credentialService.verify(user.id(), rawPassword)) {
      loginAttemptService.recordFailure(LoginAttemptService.REALM_INTERNAL, normalizedEmail);
      throw new AuthenticationFailedException();
    }

    loginAttemptService.recordSuccess(LoginAttemptService.REALM_INTERNAL, normalizedEmail);
    return issueTokens(user.id());
  }

  // Deliberately not @Transactional: refreshTokenService.rotate() already carries its own
  // transaction with noRollbackFor = InvalidRefreshTokenException (needed so reuse detection's
  // family-wide revocation UPDATE survives the exception it throws). Wrapping this method in
  // another @Transactional would join that same physical transaction under this method's default
  // rollback rule (no noRollbackFor here), silently discarding rotate()'s revocation on every
  // reuse attempt — issueAccessTokenFor() below never touches the database, so no outer
  // transaction boundary is needed here at all.
  public AccessTokenResult refresh(String refreshToken) {
    IssuedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
    String accessToken = issueAccessTokenFor(rotated.ownerId());
    return new AccessTokenResult(accessToken, rotated.rawToken(), accessTokenTtlSeconds);
  }

  public void logout(String refreshToken) {
    refreshTokenService.revoke(refreshToken);
  }

  @Transactional
  public void changePassword(UUID userId, String currentPassword, String newPassword) {
    if (!credentialService.verify(userId, currentPassword)) {
      throw new AuthenticationFailedException();
    }
    credentialService.setPassword(userId, newPassword);
    // Autorización §12: a password change must invalidate every outstanding refresh token.
    refreshTokenService.revokeAllForUser(userId);
  }

  /**
   * Always succeeds from the caller's point of view — whether or not the email matches anything
   * (autorización §11, "no revelar si el usuario existe"). {@link PasswordResetNotifier} is a
   * dev-only placeholder (§8): no real email is sent until a provider is authorized.
   */
  public void requestPasswordReset(String email) {
    String normalizedEmail = email == null ? "" : email.strip().toLowerCase();
    List<User> matches = userRepository.findAllByEmail(normalizedEmail);
    if (matches.size() != 1 || !"ACTIVE".equals(matches.get(0).status())) {
      return;
    }
    User user = matches.get(0);
    if (!credentialService.hasCredential(user.id())) {
      return;
    }
    String rawToken = OpaqueTokenGenerator.generate();
    passwordResetTokenRepository.insert(
        user.id(), TokenHasher.sha256Hex(rawToken), Instant.now().plus(passwordResetTtl));
    passwordResetNotifier.send(normalizedEmail, rawToken);
  }

  @Transactional
  public void confirmPasswordReset(String rawToken, String newPassword) {
    String hash = TokenHasher.sha256Hex(rawToken);
    UserPasswordResetToken token =
        passwordResetTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(AuthenticationFailedException::new);
    if (token.usedAt() != null || token.expiresAt().isBefore(Instant.now())) {
      throw new AuthenticationFailedException();
    }
    credentialService.setPassword(token.userId(), newPassword);
    passwordResetTokenRepository.markUsed(token.id());
    refreshTokenService.revokeAllForUser(token.userId());
  }

  private AccessTokenResult issueTokens(UUID userId) {
    String accessToken = issueAccessTokenFor(userId);
    IssuedRefreshToken refreshToken = refreshTokenService.issue(userId);
    return new AccessTokenResult(accessToken, refreshToken.rawToken(), accessTokenTtlSeconds);
  }

  private String issueAccessTokenFor(UUID userId) {
    String externalIdentityId =
        userRepository
            .findExternalIdentityId(userId)
            .orElseThrow(AuthenticationFailedException::new);
    return accessTokenIssuer.issue(externalIdentityId);
  }
}
