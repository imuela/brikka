package com.brika.platform.auth.web;

import com.brika.platform.auth.UserAuthenticationService;
import com.brika.platform.identity.User;
import com.brika.platform.security.AuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login/logout/refresh/change-password for internal users (Sprint 22 authorization Fase 2).
 * login/refresh/logout are permitted without a bearer token (SecurityConfig permitAll) — there is
 * no token yet at that point; change-password requires an already-authenticated session, exactly
 * like every other endpoint under {@code /api/v1/**}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class UserAuthController {

  private final UserAuthenticationService authenticationService;
  private final AuthorizationService authorizationService;

  public UserAuthController(
      UserAuthenticationService authenticationService, AuthorizationService authorizationService) {
    this.authenticationService = authenticationService;
    this.authorizationService = authorizationService;
  }

  @PostMapping("/login")
  public AccessTokenApiResponse login(@RequestBody LoginApiRequest request) {
    return AccessTokenApiResponse.from(
        authenticationService.login(request.email(), request.password()));
  }

  @PostMapping("/refresh")
  public AccessTokenApiResponse refresh(@RequestBody RefreshApiRequest request) {
    return AccessTokenApiResponse.from(authenticationService.refresh(request.refreshToken()));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void logout(@RequestBody LogoutApiRequest request) {
    authenticationService.logout(request.refreshToken());
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void changePassword(
      Authentication authentication, @RequestBody ChangePasswordApiRequest request) {
    User user = authorizationService.currentUser(authentication);
    authenticationService.changePassword(
        user.id(), request.currentPassword(), request.newPassword());
  }

  @PostMapping("/password-reset/request")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void requestPasswordReset(@RequestBody PasswordResetRequestApiRequest request) {
    authenticationService.requestPasswordReset(request.email());
  }

  @PostMapping("/password-reset/confirm")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void confirmPasswordReset(@RequestBody PasswordResetConfirmApiRequest request) {
    authenticationService.confirmPasswordReset(request.token(), request.newPassword());
  }
}
