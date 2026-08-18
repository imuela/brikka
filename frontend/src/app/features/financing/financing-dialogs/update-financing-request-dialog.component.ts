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
import { FINANCING_REQUEST_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { FINANCING_REQUEST_STATUSES, FinancingRequest } from '../financing.model';
import { FinancingService } from '../financing.service';

export interface UpdateFinancingRequestDialogData {
  financingRequest: FinancingRequest;
}

/** Mirrors the real PATCH /api/v1/financing-requests/{id} contract: status, requestedAmount and
 * termMonths are replaced together — there is no partial-update endpoint. */
@Component({
  selector: 'app-update-financing-request-dialog',
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
  templateUrl: './update-financing-request-dialog.component.html',
})
export class UpdateFinancingRequestDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly financingService = inject(FinancingService);
  private readonly dialogRef = inject(
    MatDialogRef<UpdateFinancingRequestDialogComponent, FinancingRequest>,
  );
  private readonly data = inject<UpdateFinancingRequestDialogData>(MAT_DIALOG_DATA);

  readonly statuses = FINANCING_REQUEST_STATUSES;
  readonly financingRequestStatusLabels = FINANCING_REQUEST_STATUS_LABELS;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    status: [this.data.financingRequest.status, Validators.required],
    requestedAmount: [this.data.financingRequest.requestedAmount.toString(), Validators.required],
    termMonths: [this.data.financingRequest.termMonths.toString(), Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    this.financingService
      .updateFinancingRequest(this.data.financingRequest.id, {
        status: value.status,
        requestedAmount: Number(value.requestedAmount),
        termMonths: Number(value.termMonths),
      })
      .subscribe({
        next: (financingRequest) => this.dialogRef.close(financingRequest),
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
