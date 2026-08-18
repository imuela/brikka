import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { Bank } from '../../banks/bank.model';
import { BankService } from '../../banks/bank.service';
import { BankRequest } from '../bank-request.model';
import { BankRequestService } from '../bank-request.service';

export interface CreateBankRequestDialogData {
  caseId: string;
}

/** bankContactId is left null — BankContact management is not part of this sprint's authorized
 * scope (the backend field is optional, per CreateBankRequestApiRequest). */
@Component({
  selector: 'app-create-bank-request-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-bank-request-dialog.component.html',
})
export class CreateBankRequestDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bankService = inject(BankService);
  private readonly bankRequestService = inject(BankRequestService);
  private readonly dialogRef = inject(
    MatDialogRef<CreateBankRequestDialogComponent, BankRequest>,
  );
  private readonly data = inject<CreateBankRequestDialogData>(MAT_DIALOG_DATA);

  readonly banks = signal<Bank[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    bankId: ['', Validators.required],
  });

  constructor() {
    this.bankService.list().subscribe({
      next: (banks) => this.banks.set(banks),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    this.bankRequestService
      .create(this.data.caseId, { bankId: value.bankId, bankContactId: null })
      .subscribe({
        next: (bankRequest) => this.dialogRef.close(bankRequest),
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
