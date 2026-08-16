package com.brika.platform.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * 19_IDENTITY_OAUTH_SPECIFICATION.md §1/§4: backend validates bearer tokens (issuer, signature,
 * expiration always; audience when configured — see JwtAudienceValidator). No login UI, no session
 * cookies: this is a stateless resource server, so CSRF protection does not apply (it defends
 * against ambient credentials like cookies, not bearer tokens the browser never attaches
 * automatically).
 *
 * <p>ADR-PORTAL-AUTH-001 (Sprint 7): two independent SecurityFilterChains. {@code
 * /api/v1/portal/**} is validated exclusively against the brika-portal realm and converted to a
 * {@link PortalAuthenticationToken} (never touches {@code users}); everything else keeps validating
 * against the internal realm exactly as before. A token issued by one realm can never authenticate
 * against the other chain — the issuer check fails before either converter runs — so this is a hard
 * separation, not just a routing convention.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final String issuerUri;
  private final String expectedAudience;
  private final String portalIssuerUri;
  private final String portalExpectedAudience;

  public SecurityConfig(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${brika.security.expected-audience:}") String expectedAudience,
      @Value("${brika.security.portal-issuer-uri}") String portalIssuerUri,
      @Value("${brika.security.portal-expected-audience:}") String portalExpectedAudience) {
    this.issuerUri = issuerUri;
    this.expectedAudience = expectedAudience;
    this.portalIssuerUri = portalIssuerUri;
    this.portalExpectedAudience = portalExpectedAudience;
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
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
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
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
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
  JwtDecoder jwtDecoder() {
    return new LazyIssuerJwtDecoder(() -> buildDecoder(issuerUri, expectedAudience));
  }

  @Bean
  PortalJwtDecoder portalJwtDecoder() {
    return new PortalJwtDecoder(
        new LazyIssuerJwtDecoder(() -> buildDecoder(portalIssuerUri, portalExpectedAudience)));
  }

  private JwtDecoder buildDecoder(String issuer, String audience) {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(JwtValidators.createDefaultWithIssuer(issuer));
    if (StringUtils.hasText(audience)) {
      validators.add(new JwtAudienceValidator(audience));
    }
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
    return decoder;
  }
}
