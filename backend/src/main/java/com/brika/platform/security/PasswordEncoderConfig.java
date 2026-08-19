package com.brika.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id, per the Sprint 22 authorization (27_KEYCLOAK_REMOVAL_ANALYSIS.md, §2/§11).
 *
 * <p>Sprint 24 (entornos/seguridad): se eleva el coste desde los defaults de Spring Security 5.8
 * (memoria 16 MiB, 2 iteraciones, paralelismo 1) hasta 32 MiB / 3 iteraciones / 1 paralelismo, con
 * salt 16 B y hash 32 B sin cambios. Cumple la recomendación OWASP (≥ 19 MiB de memoria, ≥ 2
 * iteraciones) con margen, en una máquina de desarrollo y en CI el login se mantiene en el rango de
 * ~100-150 ms. La decisión y la medición de impacto se registran en 12_DECISION_LOG.md (Sprint 24).
 *
 * <p>Nota de compatibilidad: un hash Argon2id embebe sus propios parámetros ({@code m=,t=,p=}), por
 * lo que subir memoria/iteraciones NO invalida hashes ya almacenados — {@code matches()} decodifica
 * los parámetros del propio hash. Cambiar salt/hash length sí redefiniría el encode de credenciales
 * nuevas, por eso se mantienen en los defaults.
 */
@Configuration
public class PasswordEncoderConfig {

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new Argon2PasswordEncoder(16, 32, 1, 32768, 3);
  }
}
