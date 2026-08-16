package com.brika.platform.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final String issuerUri;
  private final String expectedAudience;

  public SecurityConfig(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
      @Value("${brika.security.expected-audience:}") String expectedAudience) {
    this.issuerUri = issuerUri;
    this.expectedAudience = expectedAudience;
  }

  @Bean
  SecurityFilterChain filterChain(
      HttpSecurity http, BrikaJwtAuthenticationConverter brikaJwtAuthenticationConverter)
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
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(brikaJwtAuthenticationConverter)));
    return http.build();
  }

  @Bean
  JwtDecoder jwtDecoder() {
    return new LazyIssuerJwtDecoder(this::buildDecoder);
  }

  private JwtDecoder buildDecoder() {
    NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
    List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
    validators.add(JwtValidators.createDefaultWithIssuer(issuerUri));
    if (StringUtils.hasText(expectedAudience)) {
      validators.add(new JwtAudienceValidator(expectedAudience));
    }
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
    return decoder;
  }
}
