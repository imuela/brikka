import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { CreateTaskRequest, Task, UpdateTaskRequest } from './task.model';

/** Thin wrapper over the real /api/v1/tasks contract (17_API_SPECIFICATION_DETAILED.md §17) — no
 * fields, endpoints or business rules beyond what TaskController exposes. list() is always
 * tenant-wide (role-filtered server-side: BROKER only sees caseless tasks + tasks in cases they're
 * assigned to); there is no case-scoped tasks endpoint, so case-detail filters the same list
 * client-side by caseId instead of calling a separate endpoint. */
@Injectable({ providedIn: 'root' })
export class TaskService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<Task[]> {
    return this.apiClient.get<Task[]>('/api/v1/tasks');
  }

  create(request: CreateTaskRequest): Observable<Task> {
    return this.apiClient.post<Task>('/api/v1/tasks', request);
  }

  update(id: string, request: UpdateTaskRequest): Observable<Task> {
    return this.apiClient.patch<Task>(`/api/v1/tasks/${id}`, request);
  }

  complete(id: string): Observable<Task> {
    return this.apiClient.post<Task>(`/api/v1/tasks/${id}/complete`);
  }

  delete(id: string): Observable<void> {
    return this.apiClient.delete<void>(`/api/v1/tasks/${id}`);
  }
}
