package com.brika.platform.auth.web;

import com.brika.platform.auth.PortalAuthenticationService;
import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.portal.PortalAuthorizationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portal Cliente equivalent of {@link UserAuthController} — separate controller under {@code
 * /api/v1/portal/auth}, matching the physical separation of the Portal filter chain
 * (ADR-PORTAL-AUTH-001). login/refresh/logout are permitted without a bearer token.
 */
@RestController
@RequestMapping("/api/v1/portal/auth")
public class PortalAuthController {

  private final PortalAuthenticationService authenticationService;
  private final PortalAuthorizationService authorizationService;

  public PortalAuthController(
      PortalAuthenticationService authenticationService,
      PortalAuthorizationService authorizationService) {
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
    ClientPortalAccount account = authorizationService.currentAccount(authentication);
    authenticationService.changePassword(
        account.id(), request.currentPassword(), request.newPassword());
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
