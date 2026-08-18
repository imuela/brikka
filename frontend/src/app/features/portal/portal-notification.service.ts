import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { PortalNotification } from './portal-notification.model';

/** Thin wrapper over PortalNotificationController (PORTAL_NOTIFICATION_READ). markRead
 * (Sprint 19, ADR-PROCESS-007) closes the gap identified in the Sprint 19 definition — Portal
 * previously had no way to mark a notification read at all. */
@Injectable({ providedIn: 'root' })
export class PortalNotificationService {
  private readonly apiClient = inject(ApiClient);

  list(): Observable<PortalNotification[]> {
    return this.apiClient.get<PortalNotification[]>('/api/v1/portal/notifications');
  }

  markRead(id: string): Observable<PortalNotification> {
    return this.apiClient.patch<PortalNotification>(`/api/v1/portal/notifications/${id}/read`);
  }
}
