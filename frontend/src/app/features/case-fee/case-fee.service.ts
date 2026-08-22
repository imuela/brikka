import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { CaseFee, CaseFeeHistoryEntry, UpsertCaseFeeRequest } from './case-fee.model';

/** Sprint 32. Thin wrapper over /api/v1/cases/{caseId}/fee. */
@Injectable({ providedIn: 'root' })
export class CaseFeeService {
  private readonly apiClient = inject(ApiClient);

  get(caseId: string): Observable<CaseFee> {
    return this.apiClient.get<CaseFee>(`/api/v1/cases/${caseId}/fee`);
  }

  upsert(caseId: string, request: UpsertCaseFeeRequest): Observable<CaseFee> {
    return this.apiClient.put<CaseFee>(`/api/v1/cases/${caseId}/fee`, request);
  }

  history(caseId: string): Observable<CaseFeeHistoryEntry[]> {
    return this.apiClient.get<CaseFeeHistoryEntry[]>(`/api/v1/cases/${caseId}/fee/history`);
  }
}
