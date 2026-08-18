import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { BankProduct } from '../bank.model';
import { BankService } from '../bank.service';

export interface CreateBankProductDialogData {
  bankId: string;
}

@Component({
  selector: 'app-create-bank-product-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-bank-product-dialog.component.html',
})
export class CreateBankProductDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bankService = inject(BankService);
  private readonly dialogRef = inject(
    MatDialogRef<CreateBankProductDialogComponent, BankProduct>,
  );
  private readonly data = inject<CreateBankProductDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    code: ['', Validators.required],
    name: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    this.bankService
      .createProduct(this.data.bankId, { code: value.code, name: value.name, metadata: {} })
      .subscribe({
        next: (product) => this.dialogRef.close(product),
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
