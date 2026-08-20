import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CASE_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { DashboardService } from './dashboard.service';
import { Dashboard } from './dashboard.model';

/** Sprint 27, Bloque 2: role-aware operational dashboard replacing the Sprint 13 placeholder. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './dashboard.component.html',
})
export class DashboardComponent {
  private readonly dashboardService = inject(DashboardService);

  readonly dashboard = signal<Dashboard | null>(null);
  readonly error = signal<string | null>(null);
  readonly caseStatusLabels = CASE_STATUS_LABELS;

  constructor() {
    this.dashboardService.getDashboard().subscribe({
      next: (data) => this.dashboard.set(data),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }
}