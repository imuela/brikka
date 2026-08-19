import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../auth/auth.service';
import { SKIP_AUTH } from './http-context';

/** Attaches the Bearer token only to requests targeting our own API — never to the backend's own
 * unauthenticated auth endpoints (SKIP_AUTH), and never based on ambient state a browser would
 * send automatically. Sprint 19 (ADR-PROCESS-007): never attaches the internal token to
 * /api/v1/portal/** — that surface is handled exclusively by portalAuthInterceptor with its own
 * token, mirroring the backend's two independent SecurityFilterChains (ADR-PORTAL-AUTH-001). */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (
    req.context.get(SKIP_AUTH) ||
    !req.url.startsWith(environment.apiBaseUrl) ||
    req.url.includes('/api/v1/portal/')
  ) {
    return next(req);
  }

  const authService = inject(AuthService);
  const token = authService.accessToken();
  if (!token) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
