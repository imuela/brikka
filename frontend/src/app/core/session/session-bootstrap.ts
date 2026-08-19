import { AuthService } from '../../auth/auth.service';
import { PortalAuthService } from '../../portal-auth/portal-auth.service';
import { PortalSessionService } from '../../portal-auth/portal-session.service';
import { SessionService } from './session.service';

/**
 * Sprint 23 session bootstrap. Runs once during application initialization (provideAppInitializer)
 * so no protected navigation can execute before the session is correctly hydrated (the P0 root
 * cause this sprint fixes). Each surface independently:
 *   1. restores its token pair from the persisted refresh token (sessionStorage) when present;
 *   2. on recovery, hydrates its session store from /me (+ /me/permissions);
 *   3. if hydration fails, tears the auth and session state down together — never leaving the app
 *      in a partially-authenticated state.
 * Internal and Portal stay fully isolated (ADR-PORTAL-AUTH-001): separate auth services, separate
 * session stores, separate storage keys.
 */
export async function restoreAndHydrate(
  authService: AuthService,
  sessionService: SessionService,
  portalAuthService: PortalAuthService,
  portalSessionService: PortalSessionService,
): Promise<void> {
  if (await authService.restore()) {
    try {
      await sessionService.hydrate();
    } catch {
      authService.clearSession();
      sessionService.clear();
    }
  }

  if (await portalAuthService.restore()) {
    try {
      await portalSessionService.hydrate();
    } catch {
      portalAuthService.clearSession();
      portalSessionService.clear();
    }
  }
}