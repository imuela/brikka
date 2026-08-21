import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { FinancialAnalysisResult } from './financial-analysis.model';

/** Sprint 31. Thin wrapper over /api/v1/cases/{caseId}/financial-analysis — no fields or business
 * rules beyond what the backend exposes. */
@Injectable({ providedIn: 'root' })
export class FinancialAnalysisService {
  private readonly apiClient = inject(ApiClient);

  list(caseId: string): Observable<FinancialAnalysisResult[]> {
    return this.apiClient.get<FinancialAnalysisResult[]>(
      `/api/v1/cases/${caseId}/financial-analysis`,
    );
  }

  run(caseId: string): Observable<FinancialAnalysisResult[]> {
    return this.apiClient.post<FinancialAnalysisResult[]>(
      `/api/v1/cases/${caseId}/financial-analysis`,
    );
  }
}
