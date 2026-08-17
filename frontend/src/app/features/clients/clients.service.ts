import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { Client, CreateClientRequest, UpdateClientRequest } from './client.model';

/** Sprint 14: thin wrapper over the real /api/v1/clients contract (17_API_SPECIFICATION_
 * DETAILED.md §6) — no fields, endpoints, or business rules beyond what the backend exposes. */
@Injectable({ providedIn: 'root' })
export class ClientsService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<Client[]> {
    return this.apiClient.get<Client[]>('/api/v1/clients');
  }

  get(id: string): Observable<Client> {
    return this.apiClient.get<Client>(`/api/v1/clients/${id}`);
  }

  create(request: CreateClientRequest): Observable<Client> {
    return this.apiClient.post<Client>('/api/v1/clients', request);
  }

  update(id: string, request: UpdateClientRequest): Observable<Client> {
    return this.apiClient.patch<Client>(`/api/v1/clients/${id}`, request);
  }
}
