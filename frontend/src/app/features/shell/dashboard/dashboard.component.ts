import { Component, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { SessionStore } from '../../../core/session/session.store';
import { CASE_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { ManagerDashboardComponent } from './manager-dashboard/manager-dashboard.component';
import { SuperadminDashboardComponent } from './superadmin-dashboard/superadmin-dashboard.component';
import { DashboardService } from './dashboard.service';
import { Dashboard } from './dashboard.model';

/** Sprint 27, Bloque 2: role-aware operational dashboard replacing the Sprint 13 placeholder.
 * Sprint 41: SUPERADMIN gets a dedicated global overview (SuperadminDashboardComponent) instead
 * of this tenant-scoped view. Sprint 40.x: MANAGER gets its own company-scoped overview
 * (ManagerDashboardComponent) likewise. BROKER keeps exactly the markup/behavior below, unchanged. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
    SuperadminDashboardComponent,
    ManagerDashboardComponent,
  ],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  private readonly dashboardService = inject(DashboardService);
  private readonly sessionStore = inject(SessionStore);

  readonly isSuperadmin = computed(() => this.sessionStore.role() === 'SUPERADMIN');
  readonly isManager = computed(() => this.sessionStore.role() === 'MANAGER');
  readonly dashboard = signal<Dashboard | null>(null);
  readonly error = signal<string | null>(null);
  readonly caseStatusLabels = CASE_STATUS_LABELS;

  constructor() {
    if (this.isSuperadmin() || this.isManager()) {
      return;
    }
    this.dashboardService.getDashboard().subscribe({
      next: (data) => this.dashboard.set(data),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }
}