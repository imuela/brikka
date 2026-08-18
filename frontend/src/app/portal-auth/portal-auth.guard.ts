import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { PortalAuthService } from './portal-auth.service';

/** Portal counterpart of authGuard — UX-only, never a security boundary (the backend is always
 * the authority). Redirects to /portal/login, never /login. */
export const portalAuthGuard: CanActivateFn = (_route, state) => {
  const portalAuthService = inject(PortalAuthService);
  const router = inject(Router);

  if (portalAuthService.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/portal/login'], { queryParams: { returnUrl: state.url } });
};
