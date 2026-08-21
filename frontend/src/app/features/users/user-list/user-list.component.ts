import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';
import { ROLE_LABELS, USER_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { User } from '../user.model';
import { UserService } from '../user.service';

/** Tenant-wide user list (GET /api/v1/users). BROKER only has USER_READ (read-only, no action
 * buttons render); MANAGER has full CRUD within their own tenant; SUPERADMIN is GLOBAL (Sprint 27,
 * ADR-RBAC-002) — reads every company's users, and can create/update/disable them too (Sprint 28:
 * the "Nuevo usuario" action is no longer hidden for SUPERADMIN, see UserFormComponent for the
 * companyId picker it now shows for that role). */
@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatTooltipModule,
    HasPermissionDirective,
    StatusLabelPipe,
    StatusBadgeComponent,
  ],
  templateUrl: './user-list.component.html',
})
export class UserListComponent {
  private readonly userService = inject(UserService);
  private readonly dialog = inject(MatDialog);

  readonly users = signal<User[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly roleLabels = ROLE_LABELS;
  readonly userStatusLabels = USER_STATUS_LABELS;
  readonly displayedColumns = ['name', 'email', 'role', 'status', 'actions'];

  constructor() {
    this.load();
  }

  private load(): void {
    this.userService.list().subscribe({
      next: (users) => this.users.set(users),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  disable(user: User): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Deshabilitar usuario',
          message: `¿Seguro que quieres deshabilitar a "${user.firstName} ${user.lastName}"? No podrá volver a acceder a la aplicación.`,
          confirmLabel: 'Deshabilitar',
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.userService.disable(user.id).subscribe({
          next: () => this.load(),
          error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
        });
      });
  }
}
