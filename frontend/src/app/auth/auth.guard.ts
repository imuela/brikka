import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

/** Blocks navigation into authenticated routes when there is no session — this is a UX guard
 * only, never a security boundary (the backend is always the authority, 03_TECHNICAL_
 * SPECIFICATION.md §3: "El frontend nunca será una autoridad de seguridad"). */
export const authGuard: CanActivateFn = (_route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};
