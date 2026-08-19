package com.brika.platform.auth;

import com.brika.platform.crm.Client;
import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.crm.ClientPortalAccountRepository;
import com.brika.platform.crm.ClientRepository;
import com.brika.platform.security.OpaqueTokenGenerator;
import com.brika.platform.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Portal Cliente equivalent of {@link UserAuthenticationService} — deliberately a full duplicate,
 * never sharing an implementation, per ADR-PORTAL-AUTH-001. The login identifier is the client's
 * email (clients.email, which has no uniqueness constraint at all); the same "more than one match
 * is a generic failure" policy applies, extended here for consistency with the identical
 * cross-company ambiguity already authorized for the internal flow.
 */
@Service
public class PortalAuthenticationService {

  private final ClientRepository clientRepository;
  private final ClientPortalAccountRepository portalAccountRepository;
  private final PortalAccountCredentialService credentialService;
  private final PortalRefreshTokenService refreshTokenService;
  private final PortalAccessTokenIssuer accessTokenIssuer;
  private final LoginAttemptService loginAttemptService;
  private final PortalPasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordResetNotifier passwordResetNotifier;
  private final long accessTokenTtlSeconds;
  private final Duration passwordResetTtl;

  public PortalAuthenticationService(
      ClientRepository clientRepository,
      ClientPortalAccountRepository portalAccountRepository,
      PortalAccountCredentialService credentialService,
      PortalRefreshTokenService refreshTokenService,
      PortalAccessTokenIssuer accessTokenIssuer,
      LoginAttemptService loginAttemptService,
      PortalPasswordResetTokenRepository passwordResetTokenRepository,
      @Qualifier("portalPasswordResetNotifier") PasswordResetNotifier passwordResetNotifier,
      @Value("${brika.security.self-auth.access-token-ttl-seconds:900}") long accessTokenTtlSeconds,
      @Value("${brika.security.self-auth.password-reset-ttl-seconds:3600}")
          long passwordResetTtlSeconds) {
    this.clientRepository = clientRepository;
    this.portalAccountRepository = portalAccountRepository;
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
    if (loginAttemptService.isLocked(LoginAttemptService.REALM_PORTAL, normalizedEmail)) {
      throw new TooManyLoginAttemptsException();
    }

    List<Client> matches = clientRepository.findAllByEmail(normalizedEmail);
    Optional<ClientPortalAccount> account =
        matches.size() == 1
            ? portalAccountRepository.findByClientId(matches.get(0).id())
            : Optional.empty();

    if (account.isEmpty() || !"ACTIVE".equals(account.get().status())) {
      credentialService.verify(UUID.randomUUID(), rawPassword);
      loginAttemptService.recordFailure(LoginAttemptService.REALM_PORTAL, normalizedEmail);
      throw new AuthenticationFailedException();
    }

    ClientPortalAccount portalAccount = account.get();
    if (!credentialService.verify(portalAccount.id(), rawPassword)) {
      loginAttemptService.recordFailure(LoginAttemptService.REALM_PORTAL, normalizedEmail);
      throw new AuthenticationFailedException();
    }

    loginAttemptService.recordSuccess(LoginAttemptService.REALM_PORTAL, normalizedEmail);
    portalAccountRepository.updateLastLoginAt(portalAccount.id());
    return issueTokens(portalAccount);
  }

  // Deliberately not @Transactional: refreshTokenService.rotate() already carries its own
  // transaction with noRollbackFor = InvalidRefreshTokenException (needed so reuse detection's
  // family-wide revocation UPDATE survives the exception it throws). Wrapping this method in
  // another @Transactional would join that same physical transaction under this method's default
  // rollback rule (no noRollbackFor here), silently discarding rotate()'s revocation on every
  // reuse attempt — the portalAccountRepository.findById() lookup and accessTokenIssuer.issue()
  // below are both read-only/non-DB-writing, so no outer transaction boundary is needed here.
  public AccessTokenResult refresh(String refreshToken) {
    IssuedRefreshToken rotated = refreshTokenService.rotate(refreshToken);
    ClientPortalAccount account =
        portalAccountRepository
            .findById(rotated.ownerId())
            .orElseThrow(AuthenticationFailedException::new);
    String accessToken = accessTokenIssuer.issue(account.externalIdentityId());
    return new AccessTokenResult(accessToken, rotated.rawToken(), accessTokenTtlSeconds);
  }

  public void logout(String refreshToken) {
    refreshTokenService.revoke(refreshToken);
  }

  @Transactional
  public void changePassword(UUID portalAccountId, String currentPassword, String newPassword) {
    if (!credentialService.verify(portalAccountId, currentPassword)) {
      throw new AuthenticationFailedException();
    }
    credentialService.setPassword(portalAccountId, newPassword);
    refreshTokenService.revokeAllForAccount(portalAccountId);
  }

  public void requestPasswordReset(String email) {
    String normalizedEmail = email == null ? "" : email.strip().toLowerCase();
    List<Client> matches = clientRepository.findAllByEmail(normalizedEmail);
    Optional<ClientPortalAccount> account =
        matches.size() == 1
            ? portalAccountRepository.findByClientId(matches.get(0).id())
            : Optional.empty();
    if (account.isEmpty() || !"ACTIVE".equals(account.get().status())) {
      return;
    }
    ClientPortalAccount portalAccount = account.get();
    if (!credentialService.hasCredential(portalAccount.id())) {
      return;
    }
    String rawToken = OpaqueTokenGenerator.generate();
    passwordResetTokenRepository.insert(
        portalAccount.id(), TokenHasher.sha256Hex(rawToken), Instant.now().plus(passwordResetTtl));
    passwordResetNotifier.send(normalizedEmail, rawToken);
  }

  @Transactional
  public void confirmPasswordReset(String rawToken, String newPassword) {
    String hash = TokenHasher.sha256Hex(rawToken);
    PortalPasswordResetToken token =
        passwordResetTokenRepository
            .findByTokenHash(hash)
            .orElseThrow(AuthenticationFailedException::new);
    if (token.usedAt() != null || token.expiresAt().isBefore(Instant.now())) {
      throw new AuthenticationFailedException();
    }
    credentialService.setPassword(token.portalAccountId(), newPassword);
    passwordResetTokenRepository.markUsed(token.id());
    refreshTokenService.revokeAllForAccount(token.portalAccountId());
  }

  private AccessTokenResult issueTokens(ClientPortalAccount account) {
    String accessToken = accessTokenIssuer.issue(account.externalIdentityId());
    IssuedRefreshToken refreshToken = refreshTokenService.issue(account.id());
    return new AccessTokenResult(accessToken, refreshToken.rawToken(), accessTokenTtlSeconds);
  }
}
