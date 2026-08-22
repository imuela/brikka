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
import { FEE_STATUS_LABELS, FEE_TYPE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { CaseFee } from '../case-fee.model';
import { CaseFeeService } from '../case-fee.service';

export interface EditCaseFeeDialogData {
  caseId: string;
  current: CaseFee | null;
}

/** Sprint 32. calculationBase is always entered explicitly here, never pre-filled from
 * Case.requestedAmount or any financing figure — see V25 migration for why (no documented rule
 * on which amount should prevail). The backend always recomputes calculatedAmount; this form
 * never sends it. */
@Component({
  selector: 'app-edit-case-fee-dialog',
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
  templateUrl: './edit-case-fee-dialog.component.html',
})
export class EditCaseFeeDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly caseFeeService = inject(CaseFeeService);
  private readonly dialogRef = inject(MatDialogRef<EditCaseFeeDialogComponent, CaseFee>);
  private readonly data = inject<EditCaseFeeDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly feeTypeLabels = FEE_TYPE_LABELS;
  readonly feeStatusLabels = FEE_STATUS_LABELS;

  readonly form = this.fb.nonNullable.group({
    feeType: [this.data.current?.feeType ?? 'FIXED', Validators.required],
    fixedAmount: [this.data.current?.fixedAmount?.toString() ?? ''],
    percentage: [this.data.current?.percentage?.toString() ?? ''],
    calculationBase: [this.data.current?.calculationBase?.toString() ?? ''],
    status: [this.data.current?.status ?? 'PROPOSED', Validators.required],
  });

  readonly isPercentage = signal(this.data.current?.feeType === 'PERCENTAGE');

  onFeeTypeChange(feeType: string): void {
    this.isPercentage.set(feeType === 'PERCENTAGE');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    const feeType = value.feeType as 'FIXED' | 'PERCENTAGE';

    this.caseFeeService
      .upsert(this.data.caseId, {
        feeType,
        fixedAmount: feeType === 'FIXED' ? Number(value.fixedAmount) : null,
        percentage: feeType === 'PERCENTAGE' ? Number(value.percentage) : null,
        calculationBase: feeType === 'PERCENTAGE' ? Number(value.calculationBase) : null,
        status: value.status as 'PROPOSED' | 'AGREED' | 'CANCELLED',
        agreedAt: null,
      })
      .subscribe({
        next: (fee) => this.dialogRef.close(fee),
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
