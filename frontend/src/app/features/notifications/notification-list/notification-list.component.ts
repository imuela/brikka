import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { AppNotification } from '../notification.model';
import { NotificationService } from '../notification.service';

/** Always the current user's own notifications (server-scoped, see NotificationService). No
 * producer exists yet in the backend (NotificationService.create() has no real caller), so this
 * list is genuinely empty against real data today — the empty state below reflects that honestly
 * rather than showing invented/fictitious rows. */
@Component({
  selector: 'app-notification-list',
  standalone: true,
  imports: [DatePipe, MatTableModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './notification-list.component.html',
})
export class NotificationListComponent {
  private readonly notificationService = inject(NotificationService);

  readonly notifications = signal<AppNotification[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly displayedColumns = ['type', 'payload', 'createdAt', 'actions'];

  constructor() {
    this.load();
  }

  private load(): void {
    this.notificationService.list().subscribe({
      next: (notifications) => this.notifications.set(notifications),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  /** payload is an arbitrary JSON value with no fixed schema (jsonb, no documented catalog) —
   * mirrors the safe dynamic-JSON rendering already established for matching inputSnapshot/rules
   * in Sprint 16 (matching-result-detail-dialog.component.ts): primitives shown directly, objects
   * as compact JSON, never "null"/"undefined" as literal text. */
  formatPayload(payload: unknown): string {
    if (payload === null || payload === undefined) {
      return '—';
    }
    if (typeof payload === 'number' || typeof payload === 'string' || typeof payload === 'boolean') {
      return String(payload);
    }
    try {
      const json = JSON.stringify(payload);
      return json === '{}' ? '—' : json;
    } catch {
      return '—';
    }
  }

  markRead(notification: AppNotification): void {
    this.notificationService.markRead(notification.id).subscribe({
      next: () => this.load(),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }
}
