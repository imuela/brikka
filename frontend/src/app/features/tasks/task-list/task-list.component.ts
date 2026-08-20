import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { HideForRoleDirective } from '../../../shared/directives/hide-for-role.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';
import { TASK_STATUS_LABELS, TASK_TYPE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { AssignableUser } from '../../cases/case.model';
import { CasesService } from '../../cases/cases.service';
import { CreateTaskDialogComponent } from '../task-dialogs/create-task-dialog.component';
import { EditTaskDialogComponent } from '../task-dialogs/edit-task-dialog.component';
import { Task } from '../task.model';
import { TaskService } from '../task.service';

/** Tenant-wide task inbox (GET /api/v1/tasks) — the same endpoint case-detail's Tareas section
 * filters client-side by caseId; here every visible task is shown, with or without a case, exactly
 * matching what TaskController.list() returns for the current role (BROKER: caseless + assigned-
 * case tasks only; MANAGER/SUPERADMIN-with-support-session: all tenant tasks). */
@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [
    RouterLink,
    DatePipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    HasPermissionDirective,
    HideForRoleDirective,
    StatusLabelPipe,
    StatusBadgeComponent,
  ],
  templateUrl: './task-list.component.html',
})
export class TaskListComponent {
  private readonly taskService = inject(TaskService);
  private readonly casesService = inject(CasesService);
  private readonly dialog = inject(MatDialog);

  readonly tasks = signal<Task[] | null>(null);
  readonly users = signal<AssignableUser[]>([]);
  readonly error = signal<string | null>(null);
  readonly taskStatusLabels = TASK_STATUS_LABELS;
  readonly taskTypeLabels = TASK_TYPE_LABELS;
  readonly displayedColumns = [
    'title',
    'type',
    'status',
    'assignedTo',
    'case',
    'dueAt',
    'actions',
  ];

  constructor() {
    this.load();
    this.casesService.listAssignableUsers().subscribe({
      next: (users) => this.users.set(users),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  private load(): void {
    this.taskService.list().subscribe({
      next: (tasks) => this.tasks.set(tasks),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  userName(userId: string | null): string {
    if (!userId) {
      return '—';
    }
    const user = this.users().find((u) => u.id === userId);
    return user ? `${user.firstName} ${user.lastName}` : userId;
  }

  openCreate(): void {
    this.dialog
      .open(CreateTaskDialogComponent, { data: { caseId: null }, width: '420px' })
      .afterClosed()
      .subscribe((result: Task | undefined) => {
        if (result) {
          this.load();
        }
      });
  }

  openEdit(task: Task): void {
    this.dialog
      .open(EditTaskDialogComponent, { data: { task }, width: '420px' })
      .afterClosed()
      .subscribe((result: Task | undefined) => {
        if (result) {
          this.load();
        }
      });
  }

  complete(task: Task): void {
    this.taskService.complete(task.id).subscribe({
      next: () => this.load(),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  remove(task: Task): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Eliminar tarea',
          message: `¿Seguro que quieres eliminar la tarea "${task.title}"? Esta acción no se puede deshacer.`,
          confirmLabel: 'Eliminar',
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.taskService.delete(task.id).subscribe({
          next: () => this.load(),
          error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
        });
      });
  }
}
