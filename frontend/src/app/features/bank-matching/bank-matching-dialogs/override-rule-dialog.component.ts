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
import { MATCH_RESULT_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { MATCH_RESULTS, BankMatchRuleOverride } from '../bank-matching.model';
import { BankMatchingService } from '../bank-matching.service';

export interface OverrideRuleDialogData {
  ruleResultId: string;
  currentResult: string;
}

/** MANAGER/SUPERADMIN only (BANK_MATCHING_OVERRIDE, gated by the section that opens this dialog
 * — the backend is the actual authority, this is UX only). */
@Component({
  selector: 'app-override-rule-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './override-rule-dialog.component.html',
})
export class OverrideRuleDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bankMatchingService = inject(BankMatchingService);
  private readonly dialogRef = inject(
    MatDialogRef<OverrideRuleDialogComponent, BankMatchRuleOverride>,
  );
  readonly data = inject<OverrideRuleDialogData>(MAT_DIALOG_DATA);

  readonly results = MATCH_RESULTS;
  readonly matchResultLabels = MATCH_RESULT_LABELS;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    newResult: ['', Validators.required],
    reason: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    this.bankMatchingService
      .createOverride(this.data.ruleResultId, {
        previousResult: this.data.currentResult,
        newResult: value.newResult,
        reason: value.reason,
      })
      .subscribe({
        next: (override) => this.dialogRef.close(override),
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
