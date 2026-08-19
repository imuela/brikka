package com.brika.platform.auth.web;

import com.brika.platform.auth.PortalAccountCredentialService;
import com.brika.platform.auth.UserCredentialService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sprint 22 cierre, punto 4: the "flujo seguro" for setting an initial local password — for a
 * brand-new user (created via {@code UserProvisioningService.createUser}, which takes no password)
 * and for migrating an existing Keycloak-only user's identity to a local credential. Deliberately
 * outside {@code /api/v1} and outside the JWT-based SecurityConfig chains — same pattern as {@code
 * AiExtractionCallbackController} (ADR-AI-001): a shared secret checked manually, because this is
 * an operational/administrative capability, not a user-facing endpoint. It is intentionally NOT
 * reachable by end users and is not a general "admin resets any password" product feature —
 * building that (with its own RBAC permission, audit trail, and UX) is out of scope here and
 * remains a decision for a future sprint (see the Sprint 22 closure report, "decisiones
 * pendientes").
 */
@RestController
public class InternalCredentialBootstrapController {

  private final UserCredentialService userCredentialService;
  private final PortalAccountCredentialService portalAccountCredentialService;
  private final String configuredSecret;

  public InternalCredentialBootstrapController(
      UserCredentialService userCredentialService,
      PortalAccountCredentialService portalAccountCredentialService,
      @Value("${brika.security.self-auth.internal-bootstrap-secret:}") String configuredSecret) {
    this.userCredentialService = userCredentialService;
    this.portalAccountCredentialService = portalAccountCredentialService;
    this.configuredSecret = configuredSecret;
  }

  @PostMapping("/internal/auth/users/{userId}/credentials")
  public void setUserPassword(
      @PathVariable UUID userId,
      @RequestHeader(value = "X-Internal-Auth-Secret", required = false) String providedSecret,
      @RequestBody SetPasswordApiRequest request) {
    requireValidSecret(providedSecret);
    userCredentialService.setPassword(userId, request.newPassword());
  }

  @PostMapping("/internal/auth/portal-accounts/{portalAccountId}/credentials")
  public void setPortalAccountPassword(
      @PathVariable UUID portalAccountId,
      @RequestHeader(value = "X-Internal-Auth-Secret", required = false) String providedSecret,
      @RequestBody SetPasswordApiRequest request) {
    requireValidSecret(providedSecret);
    portalAccountCredentialService.setPassword(portalAccountId, request.newPassword());
  }

  private void requireValidSecret(String providedSecret) {
    if (configuredSecret.isBlank() || !secretMatches(providedSecret)) {
      throw new AccessDeniedException("Invalid or missing internal bootstrap secret.");
    }
  }

  /** Constant-time comparison (ADR-AI-001 rationale) — {@code providedSecret} may be null. */
  private boolean secretMatches(String providedSecret) {
    if (providedSecret == null) {
      return false;
    }
    return MessageDigest.isEqual(
        configuredSecret.getBytes(StandardCharsets.UTF_8),
        providedSecret.getBytes(StandardCharsets.UTF_8));
  }
}
