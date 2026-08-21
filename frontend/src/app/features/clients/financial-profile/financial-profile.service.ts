import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../../core/http/api-client';
import {
  ClientFinancialProfile,
  ClientFinancialProfileHistoryEntry,
  UpsertClientFinancialProfileRequest,
} from './financial-profile.model';

/** Sprint 30. Thin wrapper over /api/v1/clients/{clientId}/financial-profile — no fields or
 * business rules beyond what the backend exposes. */
@Injectable({ providedIn: 'root' })
export class FinancialProfileService {
  private readonly apiClient = inject(ApiClient);

  get(clientId: string): Observable<ClientFinancialProfile> {
    return this.apiClient.get<ClientFinancialProfile>(
      `/api/v1/clients/${clientId}/financial-profile`,
    );
  }

  upsert(
    clientId: string,
    request: UpsertClientFinancialProfileRequest,
  ): Observable<ClientFinancialProfile> {
    return this.apiClient.put<ClientFinancialProfile>(
      `/api/v1/clients/${clientId}/financial-profile`,
      request,
    );
  }

  history(clientId: string): Observable<ClientFinancialProfileHistoryEntry[]> {
    return this.apiClient.get<ClientFinancialProfileHistoryEntry[]>(
      `/api/v1/clients/${clientId}/financial-profile/history`,
    );
  }
}
