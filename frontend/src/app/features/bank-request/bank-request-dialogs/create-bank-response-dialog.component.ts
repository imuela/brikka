import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { BankResponseRecord } from '../bank-request.model';
import { BankRequestService } from '../bank-request.service';

export interface CreateBankResponseDialogData {
  bankRequestId: string;
}

@Component({
  selector: 'app-create-bank-response-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-bank-response-dialog.component.html',
})
export class CreateBankResponseDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bankRequestService = inject(BankRequestService);
  private readonly dialogRef = inject(
    MatDialogRef<CreateBankResponseDialogComponent, BankResponseRecord>,
  );
  private readonly data = inject<CreateBankResponseDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    summary: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    this.bankRequestService
      .createResponse(this.data.bankRequestId, { summary: value.summary, payload: {} })
      .subscribe({
        next: (response) => this.dialogRef.close(response),
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
