import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  Company,
  CompanySubscription,
  CreateCompanyRequest,
  UpdateCompanyRequest,
  UpsertCompanySubscriptionRequest,
} from './company.model';

/** Thin wrapper over the real /api/v1/companies contract (17_API_SPECIFICATION_DETAILED.md §4B).
 * list()/get() return every company for SUPERADMIN (GLOBAL scope) and only the caller's own
 * company for MANAGER (TENANT scope) — the backend applies this filtering, not this service.
 * create/suspend/delete are SUPERADMIN-only, enforced by the backend (COMPANY_CREATE/SUSPEND/
 * DELETE are never granted to MANAGER — 12_DECISION_LOG.md RBAC matrix). */
@Injectable({ providedIn: 'root' })
export class CompanyService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<Company[]> {
    return this.apiClient.get<Company[]>('/api/v1/companies');
  }

  get(id: string): Observable<Company> {
    return this.apiClient.get<Company>(`/api/v1/companies/${id}`);
  }

  create(request: CreateCompanyRequest): Observable<Company> {
    return this.apiClient.post<Company>('/api/v1/companies', request);
  }

  update(id: string, request: UpdateCompanyRequest): Observable<Company> {
    return this.apiClient.patch<Company>(`/api/v1/companies/${id}`, request);
  }

  suspend(id: string): Observable<Company> {
    return this.apiClient.post<Company>(`/api/v1/companies/${id}/suspend`, {});
  }

  delete(id: string): Observable<Company> {
    return this.apiClient.delete<Company>(`/api/v1/companies/${id}`);
  }

  getSubscription(companyId: string): Observable<CompanySubscription> {
    return this.apiClient.get<CompanySubscription>(`/api/v1/companies/${companyId}/subscription`);
  }

  upsertSubscription(
    companyId: string,
    request: UpsertCompanySubscriptionRequest,
  ): Observable<CompanySubscription> {
    return this.apiClient.put<CompanySubscription>(
      `/api/v1/companies/${companyId}/subscription`,
      request,
    );
  }

  cancelSubscription(companyId: string): Observable<CompanySubscription> {
    return this.apiClient.post<CompanySubscription>(
      `/api/v1/companies/${companyId}/subscription/cancel`,
      {},
    );
  }
}
