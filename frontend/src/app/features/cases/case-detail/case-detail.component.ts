import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { AddClientDialogComponent } from '../case-dialogs/add-client-dialog.component';
import { AssignDialogComponent } from '../case-dialogs/assign-dialog.component';
import { CancelDialogComponent } from '../case-dialogs/cancel-dialog.component';
import { ChangeStatusDialogComponent } from '../case-dialogs/change-status-dialog.component';
import { ReopenDialogComponent } from '../case-dialogs/reopen-dialog.component';
import { Case, CaseAssignment, CaseClient } from '../case.model';
import { CasesService } from '../cases.service';

@Component({
  selector: 'app-case-detail',
  standalone: true,
  imports: [
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
  ],
  templateUrl: './case-detail.component.html',
  styleUrl: './case-detail.component.scss',
})
export class CaseDetailComponent {
  private readonly casesService = inject(CasesService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  readonly caseId = this.route.snapshot.paramMap.get('id')!;
  readonly theCase = signal<Case | null>(null);
  readonly assignments = signal<CaseAssignment[] | null>(null);
  readonly clients = signal<CaseClient[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly clientColumns = ['name', 'participationType', 'isPrimary', 'actions'];
  readonly assignmentColumns = ['userId', 'assignmentType', 'active'];

  constructor() {
    this.loadCase();
    this.loadAssignments();
    this.loadClients();
  }

  private loadCase(): void {
    this.casesService.get(this.caseId).subscribe({
      next: (theCase) => this.theCase.set(theCase),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  private loadAssignments(): void {
    this.casesService.listAssignments(this.caseId).subscribe({
      next: (assignments) => this.assignments.set(assignments),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  private loadClients(): void {
    this.casesService.listClients(this.caseId).subscribe({
      next: (clients) => this.clients.set(clients),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }

  openChangeStatus(): void {
    this.dialog
      .open(ChangeStatusDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: Case | undefined) => {
        if (result) {
          this.theCase.set(result);
        }
      });
  }

  openCancel(): void {
    this.dialog
      .open(CancelDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: Case | undefined) => {
        if (result) {
          this.theCase.set(result);
        }
      });
  }

  openReopen(): void {
    this.dialog
      .open(ReopenDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: Case | undefined) => {
        if (result) {
          this.theCase.set(result);
        }
      });
  }

  openAssign(): void {
    this.dialog
      .open(AssignDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((result: unknown) => {
        if (result) {
          this.loadAssignments();
        }
      });
  }

  openAddClient(): void {
    this.dialog
      .open(AddClientDialogComponent, { data: { caseId: this.caseId }, width: '400px' })
      .afterClosed()
      .subscribe((added: boolean | undefined) => {
        if (added) {
          this.loadClients();
        }
      });
  }

  removeClient(clientId: string): void {
    this.casesService.removeClient(this.caseId, clientId).subscribe({
      next: () => this.loadClients(),
      error: (err: ApiError) => this.error.set(err.message),
    });
  }
}
