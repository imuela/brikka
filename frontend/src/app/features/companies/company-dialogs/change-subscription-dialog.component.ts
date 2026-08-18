import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { SUBSCRIPTION_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { Plan } from '../../plans/plan.model';
import { CompanySubscription } from '../company.model';
import { CompanyService } from '../company.service';

export interface ChangeSubscriptionDialogData {
  companyId: string;
  plans: Plan[];
  currentSubscription: CompanySubscription | null;
}

/** PUT /api/v1/companies/{companyId}/subscription is a real upsert (company_subscriptions.
 * company_id is UNIQUE) — this single dialog both assigns a first plan and changes an existing
 * one, matching the backend's own semantics exactly (CompanySubscriptionController javadoc). */
@Component({
  selector: 'app-change-subscription-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './change-subscription-dialog.component.html',
})
export class ChangeSubscriptionDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly companyService = inject(CompanyService);
  private readonly dialogRef =
    inject(MatDialogRef<ChangeSubscriptionDialogComponent, CompanySubscription>);
  readonly data = inject<ChangeSubscriptionDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly subscriptionStatusLabels = SUBSCRIPTION_STATUS_LABELS;
  readonly statusOptions = ['ACTIVE', 'TRIAL', 'SUSPENDED', 'CANCELLED'];

  readonly form = this.fb.nonNullable.group({
    planId: [this.data.currentSubscription?.planId ?? '', Validators.required],
    status: [this.data.currentSubscription?.status ?? 'ACTIVE', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    this.companyService.upsertSubscription(this.data.companyId, value).subscribe({
      next: (subscription) => this.dialogRef.close(subscription),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
