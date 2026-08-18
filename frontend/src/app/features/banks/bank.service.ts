import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  Bank,
  BankCriteriaVersion,
  BankProduct,
  CreateBankCriteriaVersionRequest,
  CreateBankProductRequest,
  CreateBankRequest,
} from './bank.model';

/** Sprint 16: thin wrapper over the real /api/v1/banks contract (17_API_SPECIFICATION_DETAILED.md
 * §12) — no fields, endpoints or business rules beyond what the backend exposes. Global catalog:
 * reads (BANK_READ/BANK_CRITERIA_READ) are available to every internal role; writes
 * (BANK_CREATE/BANK_UPDATE/BANK_CRITERIA_MANAGE) are SUPERADMIN-only, enforced by the backend. */
@Injectable({ providedIn: 'root' })
export class BankService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<Bank[]> {
    return this.apiClient.get<Bank[]>('/api/v1/banks');
  }

  get(id: string): Observable<Bank> {
    return this.apiClient.get<Bank>(`/api/v1/banks/${id}`);
  }

  create(request: CreateBankRequest): Observable<Bank> {
    return this.apiClient.post<Bank>('/api/v1/banks', request);
  }

  listProducts(bankId: string): Observable<BankProduct[]> {
    return this.apiClient.get<BankProduct[]>(`/api/v1/banks/${bankId}/products`);
  }

  createProduct(bankId: string, request: CreateBankProductRequest): Observable<BankProduct> {
    return this.apiClient.post<BankProduct>(`/api/v1/banks/${bankId}/products`, request);
  }

  listCriteria(bankId: string): Observable<BankCriteriaVersion[]> {
    return this.apiClient.get<BankCriteriaVersion[]>(`/api/v1/banks/${bankId}/criteria`);
  }

  createCriteria(
    bankId: string,
    request: CreateBankCriteriaVersionRequest,
  ): Observable<BankCriteriaVersion> {
    return this.apiClient.post<BankCriteriaVersion>(`/api/v1/banks/${bankId}/criteria`, request);
  }
}
