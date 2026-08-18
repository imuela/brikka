import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  CreateFinancingRequestRequest,
  CreateSimulationRequest,
  FinancingRequest,
  Simulation,
  UpdateFinancingRequestRequest,
} from './financing.model';

/** Sprint 16.1: thin wrapper over the real /api/v1/cases/{caseId}/simulations and
 * /api/v1/cases/{caseId}/financing-requests, /api/v1/financing-requests/{id} contracts — no
 * fields, endpoints or business rules beyond what the backend exposes. Simulation is
 * deliberately list+create only: no single-get, update or delete method, because no such
 * endpoint exists. */
@Injectable({ providedIn: 'root' })
export class FinancingService {
  private readonly apiClient = inject(ApiClient);

  listSimulations(caseId: string): Observable<Simulation[]> {
    return this.apiClient.get<Simulation[]>(`/api/v1/cases/${caseId}/simulations`);
  }

  createSimulation(caseId: string, request: CreateSimulationRequest): Observable<Simulation> {
    return this.apiClient.post<Simulation>(`/api/v1/cases/${caseId}/simulations`, request);
  }

  listFinancingRequests(caseId: string): Observable<FinancingRequest[]> {
    return this.apiClient.get<FinancingRequest[]>(`/api/v1/cases/${caseId}/financing-requests`);
  }

  createFinancingRequest(
    caseId: string,
    request: CreateFinancingRequestRequest,
  ): Observable<FinancingRequest> {
    return this.apiClient.post<FinancingRequest>(
      `/api/v1/cases/${caseId}/financing-requests`,
      request,
    );
  }

  updateFinancingRequest(
    id: string,
    request: UpdateFinancingRequestRequest,
  ): Observable<FinancingRequest> {
    return this.apiClient.patch<FinancingRequest>(`/api/v1/financing-requests/${id}`, request);
  }
}
