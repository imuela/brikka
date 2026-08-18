import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  BankMatchResult,
  BankMatchRuleOverride,
  CreateBankMatchRuleOverrideRequest,
} from './bank-matching.model';

/** Sprint 16: thin wrapper over the real /api/v1/cases/{caseId}/banks/{bankId}/matching,
 * /api/v1/cases/{caseId}/matching and /api/v1/bank-match-rule-results/{id}/overrides contracts
 * (ADR-BANKENGINE-001/002) — no fields, endpoints or business rules beyond what the backend
 * exposes. The matching snapshot is always built server-side; nothing is sent in the run request
 * body. */
@Injectable({ providedIn: 'root' })
export class BankMatchingService {
  private readonly apiClient = inject(ApiClient);

  run(caseId: string, bankId: string): Observable<BankMatchResult> {
    return this.apiClient.post<BankMatchResult>(
      `/api/v1/cases/${caseId}/banks/${bankId}/matching`,
    );
  }

  list(caseId: string): Observable<BankMatchResult[]> {
    return this.apiClient.get<BankMatchResult[]>(`/api/v1/cases/${caseId}/matching`);
  }

  createOverride(
    ruleResultId: string,
    request: CreateBankMatchRuleOverrideRequest,
  ): Observable<BankMatchRuleOverride> {
    return this.apiClient.post<BankMatchRuleOverride>(
      `/api/v1/bank-match-rule-results/${ruleResultId}/overrides`,
      request,
    );
  }
}
