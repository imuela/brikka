import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../../core/http/api-client';
import { Dashboard } from './dashboard.model';

/** Sprint 27, Bloque 2: role-aware dashboard (FUNCTIONAL_SPECIFICATION.md §3). */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly apiClient = inject(ApiClient);

  getDashboard(): Observable<Dashboard> {
    return this.apiClient.get<Dashboard>('/api/v1/dashboard');
  }
}