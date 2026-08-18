import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { BankOffer } from '../bank-request.model';
import { BankRequestService } from '../bank-request.service';

export interface CreateBankOfferDialogData {
  bankRequestId: string;
}

@Component({
  selector: 'app-create-bank-offer-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-bank-offer-dialog.component.html',
})
export class CreateBankOfferDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bankRequestService = inject(BankRequestService);
  private readonly dialogRef = inject(MatDialogRef<CreateBankOfferDialogComponent, BankOffer>);
  private readonly data = inject<CreateBankOfferDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    amount: ['', Validators.required],
    interestRate: ['', Validators.required],
    termMonths: ['', Validators.required],
    payment: ['', Validators.required],
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
      .createOffer(this.data.bankRequestId, {
        amount: Number(value.amount),
        interestRate: Number(value.interestRate),
        termMonths: Number(value.termMonths),
        payment: Number(value.payment),
        conditions: {},
      })
      .subscribe({
        next: (offer) => this.dialogRef.close(offer),
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
