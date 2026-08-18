import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { BankCriteriaVersion } from '../bank.model';
import { BankService } from '../bank.service';

export interface CreateBankCriteriaDialogData {
  bankId: string;
}

const DEFAULT_RULES = {
  rules: [
    {
      id: 'ltv-max',
      field: 'computed.ltv',
      operator: 'LESS_THAN_OR_EQUAL',
      value: 0.8,
      severity: 'FAIL',
      reason: 'El LTV no puede superar el 80%.',
    },
  ],
};

/** rules is a schemaless jsonb bag, validated server-side against the closed matching-engine
 * schema (ADR-BANKENGINE-001) — edited here as raw JSON rather than a rule-builder UI, since no
 * dedicated editor is part of this sprint's authorized scope. */
@Component({
  selector: 'app-create-bank-criteria-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-bank-criteria-dialog.component.html',
})
export class CreateBankCriteriaDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly bankService = inject(BankService);
  private readonly dialogRef = inject(
    MatDialogRef<CreateBankCriteriaDialogComponent, BankCriteriaVersion>,
  );
  private readonly data = inject<CreateBankCriteriaDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    version: ['v1', Validators.required],
    effectiveFrom: [new Date().toISOString().slice(0, 10), Validators.required],
    rules: [JSON.stringify(DEFAULT_RULES, null, 2), Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();

    let rules: Record<string, unknown>;
    try {
      rules = JSON.parse(value.rules);
    } catch {
      this.error.set('El JSON de reglas no es válido.');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.bankService
      .createCriteria(this.data.bankId, {
        version: value.version,
        effectiveFrom: `${value.effectiveFrom}T00:00:00Z`,
        effectiveTo: null,
        rules,
      })
      .subscribe({
        next: (criteria) => this.dialogRef.close(criteria),
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
