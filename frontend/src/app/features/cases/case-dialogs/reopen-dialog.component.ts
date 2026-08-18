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
import { CASE_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { CASE_STATUSES, Case } from '../case.model';
import { CasesService } from '../cases.service';

export interface ReopenDialogData {
  caseId: string;
}

/** Backend rejects a terminal target (COMPLETED/CANCELLED) — the frontend does not pre-filter
 * the list to avoid modeling that rule itself; the backend error surfaces as-is if chosen. */
@Component({
  selector: 'app-reopen-dialog',
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
  templateUrl: './reopen-dialog.component.html',
})
export class ReopenDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly dialogRef = inject(MatDialogRef<ReopenDialogComponent, Case>);
  private readonly data = inject<ReopenDialogData>(MAT_DIALOG_DATA);

  readonly statuses = CASE_STATUSES;
  readonly caseStatusLabels = CASE_STATUS_LABELS;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    targetStatus: ['', Validators.required],
    reason: [''],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.casesService.reopen(this.data.caseId, this.form.getRawValue()).subscribe({
      next: (theCase) => this.dialogRef.close(theCase),
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
