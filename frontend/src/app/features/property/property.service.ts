import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { Property, UpsertPropertyRequest } from './property.model';

/** Thin wrapper over /api/v1/cases/{caseId}/property (17_API_SPECIFICATION_DETAILED.md §8). */
@Injectable({ providedIn: 'root' })
export class PropertyService {
  private readonly apiClient = inject(ApiClient);

  get(caseId: string): Observable<Property> {
    return this.apiClient.get<Property>(`/api/v1/cases/${caseId}/property`);
  }

  upsert(caseId: string, request: UpsertPropertyRequest): Observable<Property> {
    return this.apiClient.put<Property>(`/api/v1/cases/${caseId}/property`, request);
  }
}
