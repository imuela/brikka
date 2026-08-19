import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { PortalAuthService } from '../../portal-auth/portal-auth.service';
import { ApiError, toApiError } from './api-error';
import { SKIP_AUTH } from './http-context';

/**
 * Normalizes backend errors into ApiError. A 401 is handled specially (session cleared, redirect
 * to the matching login page) and is never parsed as {code,message,requestId} — Spring Security's
 * own AuthenticationEntryPoint answers 401s before GlobalExceptionHandler ever runs, so that shape
 * cannot be assumed there (Sprint 13 Fase 0 finding H5).
 *
 * Sprint 19 (ADR-PROCESS-007): a single shared interceptor serves both surfaces, so a 401 must be
 * routed to whichever session actually issued the request — never the internal AuthService for a
 * /api/v1/portal/** request or vice versa, mirroring the hard separation the backend already
 * enforces with two independent SecurityFilterChains (ADR-PORTAL-AUTH-001).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_AUTH)) {
    return next(req);
  }

  const isPortalRequest = req.url.includes('/api/v1/portal/');
  const authService = inject(AuthService);
  const portalAuthService = inject(PortalAuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      if (error.status === 401) {
        if (isPortalRequest) {
          portalAuthService.clearSession();
          void router.navigate(['/portal/login']);
        } else {
          authService.clearSession();
          void router.navigate(['/login']);
        }
        return throwError(
          () =>
            ({
              status: 401,
              code: null,
              message: 'Tu sesión ha caducado. Vuelve a iniciar sesión.',
              requestId: null,
            }) satisfies ApiError,
        );
      }

      return throwError(() => toApiError(error));
    }),
  );
};
