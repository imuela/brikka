import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CreatePlanDialogComponent } from '../plan-dialogs/create-plan-dialog.component';
import { EditPlanDialogComponent } from '../plan-dialogs/edit-plan-dialog.component';
import { Plan } from '../plan.model';
import { PlanService } from '../plan.service';

/** SUPERADMIN-only catalog of plans (GET /api/v1/plans, PLAN_READ) — reads/writes gated the same
 * way as Bancos (Sprint 16): permission-gated buttons, no role branching in the template. */
@Component({
  selector: 'app-plan-list',
  standalone: true,
  imports: [
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    HasPermissionDirective,
  ],
  templateUrl: './plan-list.component.html',
})
export class PlanListComponent {
  private readonly planService = inject(PlanService);
  private readonly dialog = inject(MatDialog);

  readonly plans = signal<Plan[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly displayedColumns = ['code', 'name', 'status', 'actions'];

  constructor() {
    this.load();
  }

  private load(): void {
    this.planService.list().subscribe({
      next: (plans) => this.plans.set(plans),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  openCreate(): void {
    this.dialog
      .open(CreatePlanDialogComponent, { width: '400px' })
      .afterClosed()
      .subscribe((result: Plan | undefined) => {
        if (result) {
          this.load();
        }
      });
  }

  openEdit(plan: Plan): void {
    this.dialog
      .open(EditPlanDialogComponent, { data: { plan }, width: '400px' })
      .afterClosed()
      .subscribe((result: Plan | undefined) => {
        if (result) {
          this.load();
        }
      });
  }
}
