import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { FinancingRequest } from '../financing.model';
import { FinancingService } from '../financing.service';

export interface CreateFinancingRequestDialogData {
  caseId: string;
}

@Component({
  selector: 'app-create-financing-request-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-financing-request-dialog.component.html',
})
export class CreateFinancingRequestDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly financingService = inject(FinancingService);
  private readonly dialogRef = inject(
    MatDialogRef<CreateFinancingRequestDialogComponent, FinancingRequest>,
  );
  private readonly data = inject<CreateFinancingRequestDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    requestedAmount: ['', Validators.required],
    termMonths: ['', Validators.required],
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
      .createFinancingRequest(this.data.caseId, {
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
