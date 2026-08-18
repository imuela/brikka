import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { COMPANY_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { Company } from '../company.model';
import { CompanyService } from '../company.service';

/** GET /api/v1/companies returns every company for SUPERADMIN and only the caller's own company
 * for MANAGER (backend-enforced) — this list renders exactly what the backend returns, no
 * frontend filtering of its own. */
@Component({
  selector: 'app-company-list',
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
  templateUrl: './company-list.component.html',
})
export class CompanyListComponent {
  private readonly companyService = inject(CompanyService);

  readonly companies = signal<Company[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly companyStatusLabels = COMPANY_STATUS_LABELS;
  readonly displayedColumns = ['legalName', 'tradeName', 'taxId', 'status'];

  constructor() {
    this.companyService.list().subscribe({
      next: (companies) => this.companies.set(companies),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }
}
