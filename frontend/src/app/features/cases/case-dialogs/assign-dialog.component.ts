import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { AssignableUser, CaseAssignment } from '../case.model';
import { CasesService } from '../cases.service';

export interface AssignDialogData {
  caseId: string;
}

/**
 * `assignmentType` is free text in the backend contract (CreateCaseAssignmentApiRequest — no
 * catalog documented anywhere), so it stays a plain text field rather than an invented dropdown.
 */
@Component({
  selector: 'app-assign-dialog',
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
  templateUrl: './assign-dialog.component.html',
})
export class AssignDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly dialogRef = inject(MatDialogRef<AssignDialogComponent, CaseAssignment>);
  private readonly data = inject<AssignDialogData>(MAT_DIALOG_DATA);

  readonly users = signal<AssignableUser[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    userId: ['', Validators.required],
    assignmentType: ['', Validators.required],
  });

  constructor() {
    this.casesService.listAssignableUsers().subscribe({
      next: (users) => this.users.set(users),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.casesService.assign(this.data.caseId, this.form.getRawValue()).subscribe({
      next: (assignment) => this.dialogRef.close(assignment),
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
