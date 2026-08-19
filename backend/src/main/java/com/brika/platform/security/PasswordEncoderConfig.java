package com.brika.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id, per the Sprint 22 authorization (27_KEYCLOAK_REMOVAL_ANALYSIS.md, §2/§11). Uses Spring
 * Security's own recommended defaults (salt 16 bytes, hash 32 bytes, parallelism 1, memory 16 MiB,
 * 2 iterations) rather than hand-tuned parameters — deliberately not introducing an untested cost
 * profile without a dedicated review.
 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
  }
}
