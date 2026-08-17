import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { CASE_STATUSES, Case } from '../case.model';
import { CasesService } from '../cases.service';

export interface ChangeStatusDialogData {
  caseId: string;
}

@Component({
  selector: 'app-change-status-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './change-status-dialog.component.html',
})
export class ChangeStatusDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly dialogRef = inject(MatDialogRef<ChangeStatusDialogComponent, Case>);
  private readonly data = inject<ChangeStatusDialogData>(MAT_DIALOG_DATA);

  readonly statuses = CASE_STATUSES;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    newStatus: ['', Validators.required],
    reason: [''],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.casesService.changeStatus(this.data.caseId, this.form.getRawValue()).subscribe({
      next: (theCase) => this.dialogRef.close(theCase),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(err.message);
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
