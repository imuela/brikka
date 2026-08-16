package com.brika.platform.portal;

import com.brika.platform.crm.ClientPortalAccount;
import com.brika.platform.security.PortalAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Portal counterpart of AuthorizationService — deliberately never touches users, UserRepository, or
 * PermissionResolutionService (ADR-PORTAL-AUTH-001 point 5: a Portal principal must never be able
 * to resolve internal permissions).
 */
@Component
public class PortalAuthorizationService {

  private final PortalPermissionResolutionService portalPermissionResolutionService;

  public PortalAuthorizationService(
      PortalPermissionResolutionService portalPermissionResolutionService) {
    this.portalPermissionResolutionService = portalPermissionResolutionService;
  }

  public ClientPortalAccount currentAccount(Authentication authentication) {
    if (!(authentication instanceof PortalAuthenticationToken token)) {
      throw new AccessDeniedException("No authenticated Portal principal");
    }
    return token.account();
  }

  public void requirePermission(Authentication authentication, String permissionCode) {
    currentAccount(authentication);
    if (!portalPermissionResolutionService.portalPermissionCodes().contains(permissionCode)) {
      throw new AccessDeniedException("Missing Portal permission: " + permissionCode);
    }
  }
}
