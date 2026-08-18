import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../../auth/auth.service';
import { ApiError } from './api-error';
import { SKIP_AUTH } from './http-context';

/**
 * Normalizes backend errors into ApiError. A 401 is handled specially (session cleared, redirect
 * to /login) and is never parsed as {code,message,requestId} — Spring Security's own
 * AuthenticationEntryPoint answers 401s before GlobalExceptionHandler ever runs, so that shape
 * cannot be assumed there (Sprint 13 Fase 0 finding H5).
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_AUTH)) {
    return next(req);
  }

  const authService = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }

      if (error.status === 401) {
        authService.clearSession();
        void router.navigate(['/login']);
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

      const body = error.error as { code?: string; message?: string; requestId?: string } | null;
      const apiError: ApiError = {
        status: error.status,
        code: body?.code ?? null,
        message: body?.message ?? error.message,
        requestId: body?.requestId ?? null,
      };
      return throwError(() => apiError);
    }),
  );
};
