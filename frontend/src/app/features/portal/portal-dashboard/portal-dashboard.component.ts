import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CASE_STATUS_LABELS, OPERATION_TYPE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { PortalCase } from '../portal-case.model';
import { PortalCaseService } from '../portal-case.service';
import { PortalNotification } from '../portal-notification.model';
import { PortalNotificationService } from '../portal-notification.service';

/** Dashboard = "operaciones, estados publicados, pendientes, notificaciones" (07_PORTAL_CLIENTE.md
 * §Dashboard) — a single combined screen, not two separate pages; there is no /portal/dashboard
 * endpoint, so this composes GET /portal/cases + GET /portal/notifications client-side. */
@Component({
  selector: 'app-portal-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
    StatusBadgeComponent,
  ],
  templateUrl: './portal-dashboard.component.html',
})
export class PortalDashboardComponent {
  private readonly portalCaseService = inject(PortalCaseService);
  private readonly portalNotificationService = inject(PortalNotificationService);

  readonly cases = signal<PortalCase[] | null>(null);
  readonly notifications = signal<PortalNotification[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly caseStatusLabels = CASE_STATUS_LABELS;
  readonly operationTypeLabels = OPERATION_TYPE_LABELS;
  readonly caseColumns = ['reference', 'operationType', 'status', 'createdAt'];

  constructor() {
    this.loadCases();
    this.loadNotifications();
  }

  private loadCases(): void {
    this.portalCaseService.list().subscribe({
      next: (cases) => this.cases.set(cases),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  private loadNotifications(): void {
    this.portalNotificationService.list().subscribe({
      next: (notifications) => this.notifications.set(notifications),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

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

  markNotificationRead(notification: PortalNotification): void {
    this.portalNotificationService.markRead(notification.id).subscribe({
      next: () => this.loadNotifications(),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }
}
