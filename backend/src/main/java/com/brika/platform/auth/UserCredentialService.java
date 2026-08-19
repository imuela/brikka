package com.brika.platform.auth;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserCredentialService {

  private final UserCredentialRepository repository;
  private final PasswordEncoder passwordEncoder;

  // A real Argon2id hash of a value nobody can present as a password, generated once at startup.
  // matches() against this is always false, but it is a genuinely well-formed hash (not a
  // hand-written string), so it exercises the same decode path as a real row and keeps verify()
  // at a constant cost whether or not the identifier has a credential — the encoder never sees a
  // malformed input that could throw and short-circuit the timing.
  private final String noSuchUserPlaceholderHash;

  public UserCredentialService(
      UserCredentialRepository repository, PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.noSuchUserPlaceholderHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  /**
   * Always consults the encoder even when no credential row exists, so response timing does not
   * reveal whether the identifier exists (autorización §11, "no revelar si el usuario existe").
   */
  public boolean verify(UUID userId, String rawPassword) {
    String hash = repository.findPasswordHash(userId).orElse(noSuchUserPlaceholderHash);
    return passwordEncoder.matches(rawPassword, hash);
  }

  public boolean hasCredential(UUID userId) {
    return repository.exists(userId);
  }

  @Transactional
  public void setPassword(UUID userId, String rawPassword) {
    repository.upsert(userId, passwordEncoder.encode(rawPassword));
  }
}
