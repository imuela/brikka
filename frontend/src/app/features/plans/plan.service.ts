import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { CreatePlanRequest, Plan, UpdatePlanRequest } from './plan.model';

/** Thin wrapper over the real /api/v1/plans contract (17_API_SPECIFICATION_DETAILED.md §4B).
 * Entirely SUPERADMIN-only, enforced by the backend (PLAN_READ/PLAN_MANAGE are never granted to
 * MANAGER/BROKER — 12_DECISION_LOG.md RBAC matrix); unlike Users, this is a GLOBAL resource with
 * no requireTenant() call at all, so SUPERADMIN uses it without the SUPPORT_SESSION limitation
 * that blocks Users/Tasks/Communications. */
@Injectable({ providedIn: 'root' })
export class PlanService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<Plan[]> {
    return this.apiClient.get<Plan[]>('/api/v1/plans');
  }

  get(id: string): Observable<Plan> {
    return this.apiClient.get<Plan>(`/api/v1/plans/${id}`);
  }

  create(request: CreatePlanRequest): Observable<Plan> {
    return this.apiClient.post<Plan>('/api/v1/plans', request);
  }

  update(id: string, request: UpdatePlanRequest): Observable<Plan> {
    return this.apiClient.patch<Plan>(`/api/v1/plans/${id}`, request);
  }
}
