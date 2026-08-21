import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import {
  FINANCIAL_PROFILE_SOURCE_LABELS,
  FINANCIAL_PROFILE_STATUS_LABELS,
} from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import {
  ClientFinancialProfile,
  FINANCIAL_PROFILE_SOURCES,
  FINANCIAL_PROFILE_STATUSES,
} from './financial-profile.model';
import { FinancialProfileService } from './financial-profile.service';

export interface EditFinancialProfileDialogData {
  clientId: string;
  profile: ClientFinancialProfile | null;
}

/** Sprint 30. Every field is optional (a broker may only know some of them yet) — only
 * non-negativity is validated client-side, mirroring the backend's own validation
 * (ClientFinancialProfileService). evidenceDocumentVersionId is exposed by the API but has no
 * picker here: it would require listing documents across every case of the client, which is out
 * of the minimal scope authorized for Sprint 30 — deliberately deferred, not omitted by oversight. */
@Component({
  selector: 'app-edit-financial-profile-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './edit-financial-profile-dialog.component.html',
})
export class EditFinancialProfileDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly financialProfileService = inject(FinancialProfileService);
  private readonly dialogRef = inject(
    MatDialogRef<EditFinancialProfileDialogComponent, ClientFinancialProfile>,
  );
  private readonly data = inject<EditFinancialProfileDialogData>(MAT_DIALOG_DATA);

  readonly sources = FINANCIAL_PROFILE_SOURCES;
  readonly statuses = FINANCIAL_PROFILE_STATUSES;
  readonly sourceLabels = FINANCIAL_PROFILE_SOURCE_LABELS;
  readonly statusLabels = FINANCIAL_PROFILE_STATUS_LABELS;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    maritalStatus: [this.data.profile?.maritalStatus ?? ''],
    dependents: [this.data.profile?.dependents ?? null, Validators.min(0)],
    employmentType: [this.data.profile?.employmentType ?? ''],
    contractType: [this.data.profile?.contractType ?? ''],
    employerName: [this.data.profile?.employerName ?? ''],
    yearsEmployed: [this.data.profile?.yearsEmployed ?? null, Validators.min(0)],
    monthlyIncome: [this.data.profile?.monthlyIncome ?? null, Validators.min(0)],
    savings: [this.data.profile?.savings ?? null, Validators.min(0)],
    otherDebtsMonthlyPayment: [
      this.data.profile?.otherDebtsMonthlyPayment ?? null,
      Validators.min(0),
    ],
    creditCardDebt: [this.data.profile?.creditCardDebt ?? null, Validators.min(0)],
    source: [this.data.profile?.source ?? 'BROKER', Validators.required],
    status: [this.data.profile?.status ?? 'PENDING', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    this.financialProfileService
      .upsert(this.data.clientId, {
        maritalStatus: value.maritalStatus || null,
        dependents: value.dependents,
        employmentType: value.employmentType || null,
        contractType: value.contractType || null,
        employerName: value.employerName || null,
        yearsEmployed: value.yearsEmployed,
        monthlyIncome: value.monthlyIncome,
        savings: value.savings,
        otherDebtsMonthlyPayment: value.otherDebtsMonthlyPayment,
        creditCardDebt: value.creditCardDebt,
        source: value.source,
        status: value.status,
        evidenceDocumentVersionId: this.data.profile?.evidenceDocumentVersionId ?? null,
      })
      .subscribe({
        next: (profile) => this.dialogRef.close(profile),
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
