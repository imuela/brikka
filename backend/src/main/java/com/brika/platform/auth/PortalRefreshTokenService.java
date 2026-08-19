package com.brika.platform.auth;

import com.brika.platform.security.OpaqueTokenGenerator;
import com.brika.platform.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opaque refresh tokens for Portal Cliente accounts. Deliberately a full duplicate of {@link
 * UserRefreshTokenService} rather than a shared generic implementation — ADR-PORTAL-AUTH-001
 * requires the Portal auth stack to stay independently modifiable from the internal one, and a
 * shared service would be exactly the kind of "código de autenticación compartido" that ADR forbids
 * without an explicit exception.
 */
@Service
public class PortalRefreshTokenService {

  private final PortalRefreshTokenRepository repository;
  private final Duration ttl;

  public PortalRefreshTokenService(
      PortalRefreshTokenRepository repository,
      @Value("${brika.security.self-auth.portal-refresh-token-ttl-seconds:2592000}")
          long ttlSeconds) {
    this.repository = repository;
    this.ttl = Duration.ofSeconds(ttlSeconds);
  }

  @Transactional
  public IssuedRefreshToken issue(UUID portalAccountId) {
    UUID familyId = UUID.randomUUID();
    return issueInFamily(portalAccountId, familyId);
  }

  // noRollbackFor: see UserRefreshTokenService.rotate() — the family revocation on reuse must
  // survive the exception thrown right after it, not be undone by it.
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public IssuedRefreshToken rotate(String rawToken) {
    String hash = TokenHasher.sha256Hex(rawToken);
    PortalRefreshToken existing =
        repository
            .findByTokenHash(hash)
            .orElseThrow(
                () ->
                    new InvalidRefreshTokenException(InvalidRefreshTokenException.Reason.UNKNOWN));

    if (existing.revokedAt() != null) {
      repository.revokeFamily(existing.familyId());
      throw new InvalidRefreshTokenException(InvalidRefreshTokenException.Reason.REUSED);
    }
    if (existing.expiresAt().isBefore(Instant.now())) {
      throw new InvalidRefreshTokenException(InvalidRefreshTokenException.Reason.EXPIRED);
    }

    IssuedRefreshToken next = issueInFamily(existing.portalAccountId(), existing.familyId());
    PortalRefreshToken justIssued =
        repository.findByTokenHash(TokenHasher.sha256Hex(next.rawToken())).orElseThrow();
    repository.markReplaced(existing.id(), justIssued.id());
    return next;
  }

  @Transactional
  public void revoke(String rawToken) {
    String hash = TokenHasher.sha256Hex(rawToken);
    repository.findByTokenHash(hash).ifPresent(token -> repository.revokeFamily(token.familyId()));
  }

  @Transactional
  public void revokeAllForAccount(UUID portalAccountId) {
    repository.revokeAllForAccount(portalAccountId);
  }

  private IssuedRefreshToken issueInFamily(UUID portalAccountId, UUID familyId) {
    String rawToken = OpaqueTokenGenerator.generate();
    Instant expiresAt = Instant.now().plus(ttl);
    repository.insert(portalAccountId, familyId, TokenHasher.sha256Hex(rawToken), expiresAt);
    return new IssuedRefreshToken(rawToken, portalAccountId, familyId, expiresAt);
  }
}
