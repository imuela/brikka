import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { ASSIGNMENT_TYPE_LABELS, ROLE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { ASSIGNMENT_TYPES, AssignableUser, CaseAssignment } from '../case.model';
import { CasesService } from '../cases.service';

export interface AssignDialogData {
  caseId: string;
}

/**
 * `assignmentType` is free text in the backend contract (CreateCaseAssignmentApiRequest — no
 * catalog documented anywhere). Sprint 20 (ADR-PROCESS-008): populated from ASSIGNMENT_TYPES — a
 * frontend-only closed catalog approved explicitly by the project owner (the backend field itself
 * stays free text, no CHECK constraint).
 */
@Component({
  selector: 'app-assign-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
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
  readonly roleLabels = ROLE_LABELS;
  readonly assignmentTypes = ASSIGNMENT_TYPES;
  readonly assignmentTypeLabels = ASSIGNMENT_TYPE_LABELS;

  readonly form = this.fb.nonNullable.group({
    userId: ['', Validators.required],
    assignmentType: ['', Validators.required],
  });

  constructor() {
    this.casesService.listAssignableUsers().subscribe({
      next: (users) => this.users.set(users),
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
    this.casesService.assign(this.data.caseId, this.form.getRawValue()).subscribe({
      next: (assignment) => this.dialogRef.close(assignment),
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
