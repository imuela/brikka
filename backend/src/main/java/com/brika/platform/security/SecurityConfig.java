package com.brika.platform.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 19_IDENTITY_OAUTH_SPECIFICATION.md §1/§4: backend validates bearer tokens (issuer, signature,
 * expiration always). No login UI beyond Brika's own (Sprint 22), no session cookies: this is a
 * stateless resource server, so CSRF protection does not apply (it defends against ambient
 * credentials like cookies, not bearer tokens the browser never attaches automatically).
 *
 * <p>ADR-PORTAL-AUTH-001 (Sprint 7): two independent SecurityFilterChains. {@code
 * /api/v1/portal/**} is validated exclusively against Brika's own Portal-issued JWTs and converted
 * to a {@link PortalAuthenticationToken} (never touches {@code users}); everything else keeps
 * validating against the internal-issued JWTs. A token issued for one side can never authenticate
 * against the other chain — the issuer check fails before either converter runs — so this is a hard
 * separation, not just a routing convention.
 *
 * <p>Sprint 13 D1 (06_SECURITY_SPECIFICATION.md §9, "CORS controlado"): both chains accept
 * cross-origin requests only from the explicitly configured frontend origin(s) — never a wildcard.
 * Bearer tokens are never ambient credentials (unlike cookies), so {@code allowCredentials} stays
 * false; the browser only attaches the Authorization header because the frontend code sets it
 * explicitly per request.
 *
 * <p>Sprint 22 cierre (ADR-AUTH-001): Keycloak retired from every environment — {@link #jwtDecoder}
 * and {@link #portalJwtDecoder} now unconditionally trust Brika's own self-issued decoders ({@link
 * SelfIssuedJwtConfig}); the Keycloak-issuer {@code LazyIssuerJwtDecoder} branch that used to sit
 * behind {@code brika.security.self-auth.enabled} has been removed along with the flag itself,
 * since there is no longer a second issuer to roll back to.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final List<String> corsAllowedOrigins;

  public SecurityConfig(
      @Value("${brika.security.cors-allowed-origins:http://localhost:4200}")
          List<String> corsAllowedOrigins) {
    this.corsAllowedOrigins = corsAllowedOrigins;
  }

  @Bean
  @Order(1)
  SecurityFilterChain portalFilterChain(
      HttpSecurity http,
      PortalJwtDecoder portalJwtDecoder,
      PortalJwtAuthenticationConverter portalJwtAuthenticationConverter)
      throws Exception {
    http.securityMatcher("/api/v1/portal/**")
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/api/v1/portal/auth/login",
                        "/api/v1/portal/auth/refresh",
                        "/api/v1/portal/auth/logout",
                        "/api/v1/portal/auth/password-reset/request",
                        "/api/v1/portal/auth/password-reset/confirm")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            (OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) ->
                oauth2.jwt(
                    jwt ->
                        jwt.decoder(portalJwtDecoder.decoder())
                            .jwtAuthenticationConverter(portalJwtAuthenticationConverter)));
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain filterChain(
      HttpSecurity http,
      BrikaJwtAuthenticationConverter brikaJwtAuthenticationConverter,
      JwtDecoder jwtDecoder)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/internal/ai/**")
                    .permitAll()
                    .requestMatchers("/internal/auth/**")
                    .permitAll()
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/password-reset/request",
                        "/api/v1/auth/password-reset/confirm")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(
            (OAuth2ResourceServerConfigurer<HttpSecurity> oauth2) ->
                oauth2.jwt(
                    jwt ->
                        jwt.decoder(jwtDecoder)
                            .jwtAuthenticationConverter(brikaJwtAuthenticationConverter)));
    return http.build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsAllowedOrigins);
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    configuration.setExposedHeaders(List.of("X-Request-Id"));
    configuration.setAllowCredentials(false);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Bean
  JwtDecoder jwtDecoder(
      @Qualifier("internalSelfIssuedJwtDecoder") JwtDecoder internalSelfIssuedJwtDecoder) {
    return internalSelfIssuedJwtDecoder;
  }

  @Bean
  PortalJwtDecoder portalJwtDecoder(
      @Qualifier("portalSelfIssuedJwtDecoder") JwtDecoder portalSelfIssuedJwtDecoder) {
    return new PortalJwtDecoder(portalSelfIssuedJwtDecoder);
  }
}
