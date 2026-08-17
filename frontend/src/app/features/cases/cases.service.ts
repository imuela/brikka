import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  AddCaseClientRequest,
  AssignableUser,
  CancelCaseRequest,
  Case,
  CaseAssignment,
  CaseClient,
  ChangeCaseStatusRequest,
  CreateCaseAssignmentRequest,
  CreateCaseRequest,
  ReopenCaseRequest,
  UpdateCaseRequest,
} from './case.model';

/** Sprint 14: thin wrapper over the real /api/v1/cases contract (17_API_SPECIFICATION_DETAILED.md
 * §7) — no fields, endpoints, or business rules beyond what the backend exposes. */
@Injectable({ providedIn: 'root' })
export class CasesService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<Case[]> {
    return this.apiClient.get<Case[]>('/api/v1/cases');
  }

  get(id: string): Observable<Case> {
    return this.apiClient.get<Case>(`/api/v1/cases/${id}`);
  }

  create(request: CreateCaseRequest): Observable<Case> {
    return this.apiClient.post<Case>('/api/v1/cases', request);
  }

  update(id: string, request: UpdateCaseRequest): Observable<Case> {
    return this.apiClient.patch<Case>(`/api/v1/cases/${id}`, request);
  }

  changeStatus(id: string, request: ChangeCaseStatusRequest): Observable<Case> {
    return this.apiClient.post<Case>(`/api/v1/cases/${id}/status`, request);
  }

  cancel(id: string, request: CancelCaseRequest): Observable<Case> {
    return this.apiClient.post<Case>(`/api/v1/cases/${id}/cancel`, request);
  }

  reopen(id: string, request: ReopenCaseRequest): Observable<Case> {
    return this.apiClient.post<Case>(`/api/v1/cases/${id}/reopen`, request);
  }

  listAssignments(id: string): Observable<CaseAssignment[]> {
    return this.apiClient.get<CaseAssignment[]>(`/api/v1/cases/${id}/assignments`);
  }

  assign(id: string, request: CreateCaseAssignmentRequest): Observable<CaseAssignment> {
    return this.apiClient.post<CaseAssignment>(`/api/v1/cases/${id}/assignments`, request);
  }

  listClients(id: string): Observable<CaseClient[]> {
    return this.apiClient.get<CaseClient[]>(`/api/v1/cases/${id}/clients`);
  }

  addClient(id: string, request: AddCaseClientRequest): Observable<void> {
    return this.apiClient.post<void>(`/api/v1/cases/${id}/clients`, request);
  }

  removeClient(id: string, clientId: string): Observable<void> {
    return this.apiClient.delete<void>(`/api/v1/cases/${id}/clients/${clientId}`);
  }

  /** GET /api/v1/users (17_API_SPECIFICATION_DETAILED.md §5) — reused here only to populate the
   * assignment picker with real tenant users; no new endpoint or Users feature is introduced. */
  listAssignableUsers(): Observable<AssignableUser[]> {
    return this.apiClient.get<AssignableUser[]>('/api/v1/users');
  }
}
