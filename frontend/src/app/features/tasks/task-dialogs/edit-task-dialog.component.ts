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
import { TASK_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { AssignableUser } from '../../cases/case.model';
import { CasesService } from '../../cases/cases.service';
import { Task } from '../task.model';
import { TaskService } from '../task.service';

export interface EditTaskDialogData {
  task: Task;
}

/** Full-replace PATCH mirroring UpdateTaskApiRequest. status excludes DONE — the backend rejects
 * it here; completing a task always goes through the dedicated complete() action instead. */
@Component({
  selector: 'app-edit-task-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './edit-task-dialog.component.html',
})
export class EditTaskDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly taskService = inject(TaskService);
  private readonly dialogRef = inject(MatDialogRef<EditTaskDialogComponent, Task>);
  private readonly data = inject<EditTaskDialogData>(MAT_DIALOG_DATA);

  readonly users = signal<AssignableUser[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly taskStatusLabels = TASK_STATUS_LABELS;
  readonly updatableStatuses = ['TODO', 'IN_PROGRESS', 'BLOCKED', 'CANCELLED'];

  readonly form = this.fb.nonNullable.group({
    title: [this.data.task.title, Validators.required],
    description: [this.data.task.description ?? ''],
    status: [this.data.task.status, Validators.required],
    assignedTo: [this.data.task.assignedTo ?? ''],
    dueAt: [this.data.task.dueAt ? this.data.task.dueAt.substring(0, 10) : ''],
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
      .update(this.data.task.id, {
        title: value.title,
        description: value.description || null,
        status: value.status,
        assignedTo: value.assignedTo || null,
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
