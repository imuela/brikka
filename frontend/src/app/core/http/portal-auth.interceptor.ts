import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { PortalAuthService } from '../../portal-auth/portal-auth.service';
import { SKIP_AUTH } from './http-context';

/** Portal counterpart of authInterceptor (Sprint 19, ADR-PROCESS-007) — attaches the Portal
 * Bearer token only to /api/v1/portal/** requests, from PortalAuthService's own token store.
 * Never attaches it to any other request, and authInterceptor never attaches the internal token
 * here either — the two interceptors partition all API traffic by URL, with no overlap. */
export const portalAuthInterceptor: HttpInterceptorFn = (req, next) => {
  if (req.context.get(SKIP_AUTH) || !req.url.includes('/api/v1/portal/')) {
    return next(req);
  }

  const portalAuthService = inject(PortalAuthService);
  const token = portalAuthService.accessToken();
  if (!token) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
