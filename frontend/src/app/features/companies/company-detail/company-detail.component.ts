import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';
import { SessionStore } from '../../../core/session/session.store';
import { COMPANY_STATUS_LABELS, SUBSCRIPTION_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { Plan } from '../../plans/plan.model';
import { PlanService } from '../../plans/plan.service';
import {
  ChangeSubscriptionDialogComponent,
} from '../company-dialogs/change-subscription-dialog.component';
import { Company, CompanySubscription } from '../company.model';
import { CompanyService } from '../company.service';

/** Suscripción is a section embedded here (same pattern as case-detail's sub-sections) rather
 * than a screen of its own — the real API only exposes it nested under a company
 * (/companies/{id}/subscription), with no standalone endpoint, and it is SUPERADMIN-only
 * (SUBSCRIPTION_READ/MANAGE, never granted to MANAGER — 12_DECISION_LOG.md RBAC matrix). Unlike
 * every other embedded section in this codebase, this is the first case where the viewer can
 * genuinely lack read access to an embedded section (MANAGER on their own company) — so, unlike
 * case-detail's sections, the subscription/plans requests are only fired when the session actually
 * has SUBSCRIPTION_READ, to avoid a spurious 403 landing in the page-level error banner for a
 * section that is correctly hidden. */
@Component({
  selector: 'app-company-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
    StatusLabelPipe,
  ],
  templateUrl: './company-detail.component.html',
})
export class CompanyDetailComponent {
  private readonly companyService = inject(CompanyService);
  private readonly planService = inject(PlanService);
  private readonly sessionStore = inject(SessionStore);
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);

  private readonly companyId = this.route.snapshot.paramMap.get('id')!;

  readonly company = signal<Company | null>(null);
  readonly error = signal<string | null>(null);
  readonly companyStatusLabels = COMPANY_STATUS_LABELS;
  readonly subscriptionStatusLabels = SUBSCRIPTION_STATUS_LABELS;

  readonly subscription = signal<CompanySubscription | null>(null);
  readonly subscriptionLoading = signal(false);
  readonly plans = signal<Plan[]>([]);

  constructor() {
    this.companyService.get(this.companyId).subscribe({
      next: (company) => this.company.set(company),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });

    if (this.sessionStore.hasPermission('SUBSCRIPTION_READ')) {
      this.loadSubscription();
      this.planService.list().subscribe({
        next: (plans) => this.plans.set(plans),
        error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
      });
    }
  }

  private loadSubscription(): void {
    this.subscriptionLoading.set(true);
    this.companyService.getSubscription(this.companyId).subscribe({
      next: (subscription) => {
        this.subscription.set(subscription);
        this.subscriptionLoading.set(false);
      },
      error: (err: ApiError) => {
        this.subscriptionLoading.set(false);
        if (err.status !== 404) {
          this.error.set(friendlyErrorMessage(err));
        }
      },
    });
  }

  planName(planId: string): string {
    const plan = this.plans().find((p) => p.id === planId);
    return plan ? plan.name : planId;
  }

  suspend(): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Suspender empresa',
          message: `¿Seguro que quieres suspender "${this.company()!.legalName}"?`,
          confirmLabel: 'Suspender',
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.companyService.suspend(this.companyId).subscribe({
          next: (company) => this.company.set(company),
          error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
        });
      });
  }

  remove(): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Eliminar empresa',
          message: `¿Seguro que quieres eliminar "${this.company()!.legalName}"? Esta acción no se puede deshacer.`,
          confirmLabel: 'Eliminar',
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.companyService.delete(this.companyId).subscribe({
          next: (company) => this.company.set(company),
          error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
        });
      });
  }

  openChangeSubscription(): void {
    this.dialog
      .open(ChangeSubscriptionDialogComponent, {
        data: {
          companyId: this.companyId,
          plans: this.plans(),
          currentSubscription: this.subscription(),
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((result: CompanySubscription | undefined) => {
        if (result) {
          this.subscription.set(result);
        }
      });
  }

  cancelSubscription(): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Cancelar suscripción',
          message: '¿Seguro que quieres cancelar la suscripción de esta empresa?',
          confirmLabel: 'Cancelar suscripción',
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.companyService.cancelSubscription(this.companyId).subscribe({
          next: (subscription) => this.subscription.set(subscription),
          error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
        });
      });
  }
}
