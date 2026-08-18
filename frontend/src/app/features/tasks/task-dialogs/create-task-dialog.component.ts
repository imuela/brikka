import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { AssignableUser } from '../../cases/case.model';
import { CasesService } from '../../cases/cases.service';
import { Task } from '../task.model';
import { TaskService } from '../task.service';

export interface CreateTaskDialogData {
  /** Set to a case id when opened from case-detail (case-linked task); set to null from the
   * tenant-wide Tareas page (caseless task) — mirrors CreateTaskApiRequest.caseId being
   * nullable. Always provided explicitly by the caller, same convention as every other dialog. */
  caseId: string | null;
}

/** type is free text (varchar(100), no CHECK constraint — see Task.java), so it stays a plain
 * text field rather than an invented catalog, same reasoning as assignmentType in
 * AssignDialogComponent. */
@Component({
  selector: 'app-create-task-dialog',
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
  templateUrl: './create-task-dialog.component.html',
})
export class CreateTaskDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly taskService = inject(TaskService);
  private readonly dialogRef = inject(MatDialogRef<CreateTaskDialogComponent, Task>);
  private readonly data = inject<CreateTaskDialogData>(MAT_DIALOG_DATA);

  readonly users = signal<AssignableUser[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    type: ['', Validators.required],
    title: ['', Validators.required],
    description: [''],
    assignedTo: [''],
    dueAt: [''],
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
    const value = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.taskService
      .create({
        caseId: this.data.caseId ?? null,
        assignedTo: value.assignedTo || null,
        type: value.type,
        title: value.title,
        description: value.description || null,
        dueAt: value.dueAt ? new Date(value.dueAt).toISOString() : null,
      })
      .subscribe({
        next: (task) => this.dialogRef.close(task),
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
