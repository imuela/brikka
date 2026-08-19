package com.brika.platform.auth;

import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalAccountCredentialService {

  private final PortalAccountCredentialRepository repository;
  private final PasswordEncoder passwordEncoder;
  private final String noSuchAccountPlaceholderHash;

  public PortalAccountCredentialService(
      PortalAccountCredentialRepository repository, PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
    this.noSuchAccountPlaceholderHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  public boolean verify(UUID portalAccountId, String rawPassword) {
    String hash = repository.findPasswordHash(portalAccountId).orElse(noSuchAccountPlaceholderHash);
    return passwordEncoder.matches(rawPassword, hash);
  }

  public boolean hasCredential(UUID portalAccountId) {
    return repository.exists(portalAccountId);
  }

  @Transactional
  public void setPassword(UUID portalAccountId, String rawPassword) {
    repository.upsert(portalAccountId, passwordEncoder.encode(rawPassword));
  }
}
