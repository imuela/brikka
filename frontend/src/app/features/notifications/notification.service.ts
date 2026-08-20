import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { AppNotification } from './notification.model';

/** Thin wrapper over the real /api/v1/notifications contract
 * (17_API_SPECIFICATION_DETAILED.md §17C). Always scoped server-side to the calling user's own
 * notifications — there is no tenant-wide or other-user view, regardless of role. */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<AppNotification[]> {
    return this.apiClient.get<AppNotification[]>('/api/v1/notifications');
  }

  /** Sprint 25: unread count for the nav badge, scoped server-side to the calling user. */
  unreadCount(): Observable<number> {
    return this.apiClient
      .get<{ count: number }>('/api/v1/notifications/unread-count')
      .pipe(map((r) => r.count));
  }

  markRead(id: string): Observable<AppNotification> {
    return this.apiClient.patch<AppNotification>(`/api/v1/notifications/${id}/read`);
  }
}
