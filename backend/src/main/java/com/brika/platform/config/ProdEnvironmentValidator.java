package com.brika.platform.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Sprint 24 (environments): PROD arranca en modo "fail-closed". Si el perfil {@code prod} está
 * activo y se detecta alguna condición insegura, se aborta el arranque con un mensaje que enumera
 * todos los fallos a la vez en lugar de arrancar con configuraciones por defecto peligrosas.
 *
 * <p>Reglas que verifica en PROD (config-resolvida ya con las variables de entorno y los ficheros
 * de perfil cargados):
 *
 * <ul>
 *   <li>Claves JWT obligatorias: {@code brika.security.self-auth.{internal,portal}-signing-key-pem}
 *       deben estar presentes (si no, {@link com.brika.platform.security.SelfIssuedTokenKeys}
 *       generaría un par efímero y los tokens dejarían de validar tras un reinicio).
 *   <li>Email siempre SMTP: {@code brika.notifications.email-transport} debe ser {@code smtp}
 *       (nunca {@code noop} en producción, ADR-NOTIF-001 D8-2) y {@code SMTP_HOST} presente.
 *   <li>Seed reproducible prohibido: {@code brika.seed.enabled} no puede ser {@code true} en PROD.
 *   <li>CORS controlado: {@code brika.security.cors-allowed-origins} no puede quedar vacío, no
 *       puede contener un comodín ni la cadena {@code localhost} (06_SECURITY_SPECIFICATION.md §9).
 *   <li>Sprint 39 audit (D39-5): {@code MINIO_ROOT_USER}/{@code MINIO_ROOT_PASSWORD}/{@code
 *       RABBITMQ_USER}/{@code RABBITMQ_PASSWORD}/{@code DB_USER}/{@code DB_PASSWORD} obligatorias —
 *       {@code application.yml} da a las propiedades Spring correspondientes ({@code
 *       brika.storage.access-key}/{@code secret-key}, {@code spring.rabbitmq.username}/{@code
 *       password}, {@code spring.datasource.username}/{@code password}) un valor por defecto no
 *       vacío ({@code brika}/{@code brika_dev_password}, el mismo par que usa {@code
 *       docs/docker-compose.yml} en local), así que se comprueban las variables de entorno en
 *       crudo, no la propiedad resuelta (igual que {@code SMTP_HOST} más arriba) — de lo contrario
 *       un PROD que olvide fijarlas arrancaría en silencio con credenciales de desarrollo conocidas
 *       en vez de fallar.
 * </ul>
 *
 * <p>Registrado como {@code EnvironmentPostProcessor} (META-INF/spring/…), corre antes de crear
 * ningún bean, por lo que el fallo es temprano y con mensaje accionable.
 */
public class ProdEnvironmentValidator implements EnvironmentPostProcessor, Ordered {

  private static final String PROD = "prod";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (!hasProfile(environment, PROD)) {
      return;
    }

    List<String> violations = new ArrayList<>();

    for (String key :
        new String[] {
          "brika.security.self-auth.internal-signing-key-pem",
          "brika.security.self-auth.portal-signing-key-pem",
          "SMTP_HOST",
          "brika.security.cors-allowed-origins",
          // D39-5: raw env var names, not the Spring property path — application.yml gives
          // brika.storage.access-key/secret-key and spring.rabbitmq.username/password non-blank
          // defaults ("brika"/"brika_dev_password"), so checking the resolved property would
          // always pass even when the real env var is unset, same reason SMTP_HOST above is
          // checked as a raw var instead of "spring.mail.host".
          "MINIO_ROOT_USER",
          "MINIO_ROOT_PASSWORD",
          "RABBITMQ_USER",
          "RABBITMQ_PASSWORD",
          "DB_USER",
          "DB_PASSWORD",
        }) {
      if (!StringUtils.hasText(environment.getProperty(key))) {
        violations.add("Falta el secreto/configuración obligatoria '" + key + "' en PROD");
      }
    }

    String transport = environment.getProperty("brika.notifications.email-transport", "noop");
    if (!"smtp".equalsIgnoreCase(transport)) {
      violations.add(
          "PROD exige email-transport=smtp (nunca 'noop'); actual: '"
              + transport
              + "' (ADR-NOTIF-001 D8-2)");
    }

    if (Boolean.parseBoolean(environment.getProperty("brika.seed.enabled", "false"))) {
      violations.add("brika.seed.enabled=true está prohibido en PROD (seed reproducible)");
    }

    String cors = environment.getProperty("brika.security.cors-allowed-origins", "");
    if (cors.contains("*") || cors.toLowerCase().contains("localhost")) {
      violations.add(
          "CORS no puede contener comodines ni 'localhost' en PROD (06_SECURITY_SPECIFICATION.md"
              + " §9); actual: '"
              + cors
              + "'");
    }

    if (!violations.isEmpty()) {
      throw new IllegalStateException(
          "Arranque PROD abortado por configuración insegura (fail-closed, Sprint 24):\n  - "
              + String.join("\n  - ", violations));
    }
  }

  private static boolean hasProfile(Environment environment, String profile) {
    return Arrays.asList(environment.getActiveProfiles()).contains(profile);
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
