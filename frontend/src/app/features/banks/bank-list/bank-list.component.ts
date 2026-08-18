import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { BANK_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { Bank } from '../bank.model';
import { BankService } from '../bank.service';
import { CreateBankDialogComponent } from '../bank-dialogs/create-bank-dialog.component';

/** Global bank catalog (06_BANK_ENGINE_SPECIFICATION.md §2) — read-only for Broker/Manager
 * (BANK_READ), creation restricted to SUPERADMIN (BANK_CREATE). */
@Component({
  selector: 'app-bank-list',
  standalone: true,
  imports: [
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
    StatusLabelPipe,
  ],
  templateUrl: './bank-list.component.html',
})
export class BankListComponent {
  private readonly bankService = inject(BankService);
  private readonly dialog = inject(MatDialog);

  readonly banks = signal<Bank[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly displayedColumns = ['code', 'name', 'status'];
  readonly bankStatusLabels = BANK_STATUS_LABELS;

  constructor() {
    this.load();
  }

  private load(): void {
    this.bankService.list().subscribe({
      next: (banks) => this.banks.set(banks),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  openCreateBank(): void {
    this.dialog
      .open(CreateBankDialogComponent, { width: '400px' })
      .afterClosed()
      .subscribe((result: Bank | undefined) => {
        if (result) {
          this.load();
        }
      });
  }
}
