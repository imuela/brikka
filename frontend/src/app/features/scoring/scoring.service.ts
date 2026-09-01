import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { CaseRag } from './scoring.model';

/**
 * BRIKKA V2 I2. Thin wrapper over the case scoring endpoints. The RAG indicator is read from
 * GET /api/v1/cases/{caseId}/scoring/rag; recomputing the operation scoring reuses the existing
 * POST /api/v1/cases/{caseId}/scoring/run (no new endpoint). No business rules live here.
 */
@Injectable({ providedIn: 'root' })
export class ScoringService {
  private readonly apiClient = inject(ApiClient);

  getRag(caseId: string): Observable<CaseRag> {
    return this.apiClient.get<CaseRag>(`/api/v1/cases/${caseId}/scoring/rag`);
  }

  run(caseId: string): Observable<unknown> {
    return this.apiClient.post<unknown>(`/api/v1/cases/${caseId}/scoring/run`);
  }
}
