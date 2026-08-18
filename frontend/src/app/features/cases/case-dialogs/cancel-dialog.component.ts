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
import { CANCELLATION_REASON_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { CANCELLATION_REASONS, Case } from '../case.model';
import { CasesService } from '../cases.service';

export interface CancelDialogData {
  caseId: string;
}

@Component({
  selector: 'app-cancel-dialog',
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
  templateUrl: './cancel-dialog.component.html',
})
export class CancelDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly dialogRef = inject(MatDialogRef<CancelDialogComponent, Case>);
  private readonly data = inject<CancelDialogData>(MAT_DIALOG_DATA);

  readonly reasons = CANCELLATION_REASONS;
  readonly cancellationReasonLabels = CANCELLATION_REASON_LABELS;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    reason: ['', Validators.required],
    comment: [''],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.casesService.cancel(this.data.caseId, this.form.getRawValue()).subscribe({
      next: (theCase) => this.dialogRef.close(theCase),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  cancelDialog(): void {
    this.dialogRef.close();
  }
}
