import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { Simulation } from '../financing.model';
import { FinancingService } from '../financing.service';

export interface CreateSimulationDialogData {
  caseId: string;
}

/** Simulation has no update/delete endpoint (17_API_SPECIFICATION_DETAILED.md §11) — this dialog
 * only ever creates. metadata is a schemaless jsonb bag on the backend with no documented shape
 * for the extra fields FUNCTIONAL_SPECIFICATION.md §10 mentions (gastos, aportación, etc.); sent
 * empty rather than inventing form fields for an unspecified schema. */
@Component({
  selector: 'app-create-simulation-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-simulation-dialog.component.html',
})
export class CreateSimulationDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly financingService = inject(FinancingService);
  private readonly dialogRef = inject(MatDialogRef<CreateSimulationDialogComponent, Simulation>);
  private readonly data = inject<CreateSimulationDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    principal: ['', Validators.required],
    interestRate: ['', Validators.required],
    termMonths: ['', Validators.required],
    estimatedPayment: ['', Validators.required],
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
      .createSimulation(this.data.caseId, {
        principal: Number(value.principal),
        interestRate: Number(value.interestRate),
        termMonths: Number(value.termMonths),
        estimatedPayment: Number(value.estimatedPayment),
        metadata: {},
      })
      .subscribe({
        next: (simulation) => this.dialogRef.close(simulation),
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
