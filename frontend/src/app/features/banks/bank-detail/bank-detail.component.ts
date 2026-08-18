import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { BANK_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { Bank, BankCriteriaVersion, BankProduct } from '../bank.model';
import { BankService } from '../bank.service';
import { CreateBankCriteriaDialogComponent } from '../bank-dialogs/create-bank-criteria-dialog.component';
import { CreateBankProductDialogComponent } from '../bank-dialogs/create-bank-product-dialog.component';

@Component({
  selector: 'app-bank-detail',
  standalone: true,
  imports: [
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatTableModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
    StatusLabelPipe,
  ],
  templateUrl: './bank-detail.component.html',
})
export class BankDetailComponent {
  private readonly bankService = inject(BankService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);

  readonly bankId = this.route.snapshot.paramMap.get('id')!;
  readonly bank = signal<Bank | null>(null);
  readonly error = signal<string | null>(null);
  readonly bankStatusLabels = BANK_STATUS_LABELS;

  readonly products = signal<BankProduct[] | null>(null);
  readonly productColumns = ['code', 'name', 'status'];

  readonly criteria = signal<BankCriteriaVersion[] | null>(null);
  readonly criteriaColumns = ['version', 'status', 'effectiveFrom', 'effectiveTo'];

  constructor() {
    this.bankService.get(this.bankId).subscribe({
      next: (bank) => this.bank.set(bank),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    this.loadProducts();
    this.loadCriteria();
  }

  private loadProducts(): void {
    this.bankService.listProducts(this.bankId).subscribe({
      next: (products) => this.products.set(products),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  openCreateProduct(): void {
    this.dialog
      .open(CreateBankProductDialogComponent, { data: { bankId: this.bankId }, width: '400px' })
      .afterClosed()
      .subscribe((result: BankProduct | undefined) => {
        if (result) {
          this.loadProducts();
        }
      });
  }

  private loadCriteria(): void {
    this.bankService.listCriteria(this.bankId).subscribe({
      next: (criteria) => this.criteria.set(criteria),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  openCreateCriteria(): void {
    this.dialog
      .open(CreateBankCriteriaDialogComponent, { data: { bankId: this.bankId }, width: '480px' })
      .afterClosed()
      .subscribe((result: BankCriteriaVersion | undefined) => {
        if (result) {
          this.loadCriteria();
        }
      });
  }
}
