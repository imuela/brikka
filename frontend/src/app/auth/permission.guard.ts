import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { SessionStore } from '../core/session/session.store';

/** Reads the required permission code from the route's `data.permission`. UX-only, like
 * authGuard — never a substitute for backend authorization. */
export const permissionGuard: CanActivateFn = (route) => {
  const sessionStore = inject(SessionStore);
  const router = inject(Router);

  const requiredPermission = route.data['permission'] as string | undefined;
  if (!requiredPermission || sessionStore.hasPermission(requiredPermission)) {
    return true;
  }

  return router.createUrlTree(['/app/forbidden']);
};
