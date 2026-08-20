import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { HideForRoleDirective } from '../../../shared/directives/hide-for-role.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CASE_STATUS_LABELS, OPERATION_TYPE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { Case } from '../case.model';
import { CasesService } from '../cases.service';

/** Backend already filters this list by role — BROKER sees only assigned cases, MANAGER/
 * SUPERADMIN see the whole tenant (CaseController.list) — the frontend applies no filtering of
 * its own. */
@Component({
  selector: 'app-case-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
    HideForRoleDirective,
    StatusLabelPipe,
    StatusBadgeComponent,
  ],
  templateUrl: './case-list.component.html',
  styleUrl: './case-list.component.scss',
})
export class CaseListComponent {
  private readonly casesService = inject(CasesService);

  readonly cases = signal<Case[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly displayedColumns = ['reference', 'operationType', 'status', 'createdAt'];
  readonly caseStatusLabels = CASE_STATUS_LABELS;
  readonly operationTypeLabels = OPERATION_TYPE_LABELS;

  constructor() {
    this.casesService.list().subscribe({
      next: (cases) => this.cases.set(cases),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }
}
