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
 * Opaque refresh tokens for internal users (SUPERADMIN/MANAGER/BROKER). Only a SHA-256 hash is ever
 * persisted (autorización Sprint 22 §2). Rotation: every successful refresh issues a new token in
 * the same family and immediately revokes the one just used; presenting an already-revoked token is
 * treated as reuse (token theft signal) and revokes the entire family, per the authorization's
 * explicit "detección de reutilización" requirement.
 */
@Service
public class UserRefreshTokenService {

  private final UserRefreshTokenRepository repository;
  private final Duration ttl;

  public UserRefreshTokenService(
      UserRefreshTokenRepository repository,
      @Value("${brika.security.self-auth.refresh-token-ttl-seconds:2592000}") long ttlSeconds) {
    this.repository = repository;
    this.ttl = Duration.ofSeconds(ttlSeconds);
  }

  @Transactional
  public IssuedRefreshToken issue(UUID userId) {
    UUID familyId = UUID.randomUUID();
    return issueInFamily(userId, familyId);
  }

  // noRollbackFor: on reuse, revokeFamily()'s UPDATE must survive even though the method then
  // throws — the whole point of reuse detection is that the revocation is persisted, not undone
  // by the default unchecked-exception rollback.
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public IssuedRefreshToken rotate(String rawToken) {
    String hash = TokenHasher.sha256Hex(rawToken);
    UserRefreshToken existing =
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

    IssuedRefreshToken next = issueInFamily(existing.userId(), existing.familyId());
    UserRefreshToken justIssued =
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
  public void revokeAllForUser(UUID userId) {
    repository.revokeAllForUser(userId);
  }

  private IssuedRefreshToken issueInFamily(UUID userId, UUID familyId) {
    String rawToken = OpaqueTokenGenerator.generate();
    Instant expiresAt = Instant.now().plus(ttl);
    repository.insert(userId, familyId, TokenHasher.sha256Hex(rawToken), expiresAt);
    return new IssuedRefreshToken(rawToken, userId, familyId, expiresAt);
  }
}
