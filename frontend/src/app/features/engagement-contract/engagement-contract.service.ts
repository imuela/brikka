import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { GeneratedDocument, GeneratedDocumentVersion } from './engagement-contract.model';

/** Sprint 32. Thin wrapper over /api/v1/cases/{caseId}/contract. */
@Injectable({ providedIn: 'root' })
export class EngagementContractService {
  private readonly apiClient = inject(ApiClient);

  get(caseId: string): Observable<GeneratedDocument> {
    return this.apiClient.get<GeneratedDocument>(`/api/v1/cases/${caseId}/contract`);
  }

  generate(caseId: string): Observable<GeneratedDocumentVersion> {
    return this.apiClient.post<GeneratedDocumentVersion>(`/api/v1/cases/${caseId}/contract`);
  }
}
