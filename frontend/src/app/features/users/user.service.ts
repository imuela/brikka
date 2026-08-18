import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { CreateUserRequest, UpdateUserRequest, User } from './user.model';

/** Thin wrapper over the real /api/v1/users contract (17_API_SPECIFICATION_DETAILED.md §5). Scope
 * is always the caller's own tenant, resolved server-side — no company_id is ever sent from here.
 * SUPERADMIN never resolves a tenant without an active SUPPORT_SESSION (not implemented), so every
 * call here returns 403 for that role; the frontend does not work around this. */
@Injectable({ providedIn: 'root' })
export class UserService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<User[]> {
    return this.apiClient.get<User[]>('/api/v1/users');
  }

  get(id: string): Observable<User> {
    return this.apiClient.get<User>(`/api/v1/users/${id}`);
  }

  create(request: CreateUserRequest): Observable<User> {
    return this.apiClient.post<User>('/api/v1/users', request);
  }

  update(id: string, request: UpdateUserRequest): Observable<User> {
    return this.apiClient.patch<User>(`/api/v1/users/${id}`, request);
  }

  disable(id: string): Observable<User> {
    return this.apiClient.post<User>(`/api/v1/users/${id}/disable`, {});
  }
}
