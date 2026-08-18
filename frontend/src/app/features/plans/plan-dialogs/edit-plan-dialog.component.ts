import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { Plan } from '../plan.model';
import { PlanService } from '../plan.service';

export interface EditPlanDialogData {
  plan: Plan;
}

@Component({
  selector: 'app-edit-plan-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './edit-plan-dialog.component.html',
})
export class EditPlanDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly planService = inject(PlanService);
  private readonly dialogRef = inject(MatDialogRef<EditPlanDialogComponent, Plan>);
  readonly data = inject<EditPlanDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: [this.data.plan.name, Validators.required],
    status: [this.data.plan.status, Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    this.planService.update(this.data.plan.id, value).subscribe({
      next: (plan) => this.dialogRef.close(plan),
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
