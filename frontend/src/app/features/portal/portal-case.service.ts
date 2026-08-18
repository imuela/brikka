import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { PortalCase } from './portal-case.model';

/** Thin wrapper over GET /api/v1/portal/cases[/:{id}] (PortalCaseController) — real endpoints,
 * PORTAL_CASE_READ. Isolation (tenant + client participation, cross-client masked as 404) is
 * enforced entirely server-side by PortalCaseAccessService — nothing to replicate here. */
@Injectable({ providedIn: 'root' })
export class PortalCaseService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<PortalCase[]> {
    return this.apiClient.get<PortalCase[]>('/api/v1/portal/cases');
  }

  get(id: string): Observable<PortalCase> {
    return this.apiClient.get<PortalCase>(`/api/v1/portal/cases/${id}`);
  }
}
