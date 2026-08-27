import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { forkJoin } from 'rxjs';

import { ApiError } from '../../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../../core/http/error-messages';
import { BankService } from '../../../banks/bank.service';
import { CasesService } from '../../../cases/cases.service';
import { ClientsService } from '../../../clients/clients.service';
import { CompanyService } from '../../../companies/company.service';
import { UserService } from '../../../users/user.service';

interface SuperadminMetric {
  key: string;
  label: string;
  icon: string;
  value: number;
  route: string | null;
}

/** Sprint 41: dedicated SUPERADMIN home — a global overview grid, distinct from the tenant-scoped
 * operational dashboard (DashboardComponent) that MANAGER/BROKER keep seeing unchanged. Every
 * number here comes from the real existing list endpoints (companies/users/clients/cases/banks) —
 * no new backend endpoint was introduced; the 3 derived metrics (active/success cases, active
 * users) are computed client-side from fields the backend already returns. */
@Component({
  selector: 'app-superadmin-dashboard',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './superadmin-dashboard.component.html',
  styleUrl: './superadmin-dashboard.component.scss',
})
export class SuperadminDashboardComponent {
  private readonly companyService = inject(CompanyService);
  private readonly userService = inject(UserService);
  private readonly clientsService = inject(ClientsService);
  private readonly casesService = inject(CasesService);
  private readonly bankService = inject(BankService);
  private readonly router = inject(Router);

  readonly metrics = signal<SuperadminMetric[] | null>(null);
  readonly error = signal<string | null>(null);

  constructor() {
    forkJoin({
      companies: this.companyService.list(),
      users: this.userService.list(),
      clients: this.clientsService.list(),
      cases: this.casesService.list(),
      banks: this.bankService.list(),
    }).subscribe({
      next: ({ companies, users, clients, cases, banks }) => {
        // Mirrors DashboardRepository.countActiveCases exactly: every status except the two
        // terminal ones (13_DEFINITIVE_WORKFLOW_SPECIFICATION.md §2).
        const activeCases = cases.filter(
          (c) => c.status !== 'COMPLETED' && c.status !== 'CANCELLED',
        ).length;
        const successCases = cases.filter((c) => c.status === 'COMPLETED').length;
        const activeUsers = users.filter((u) => u.status === 'ACTIVE').length;

        // Fila 1: Empresas, Usuarios, Usuarios activos, Bancos.
        // Fila 2: Clientes, Casos, Casos activos, Casos éxito.
        this.metrics.set([
          {
            key: 'companies',
            label: 'Empresas',
            icon: 'apartment',
            value: companies.length,
            route: '/app/companies',
          },
          {
            key: 'users',
            label: 'Usuarios',
            icon: 'group',
            value: users.length,
            route: '/app/users',
          },
          {
            key: 'activeUsers',
            label: 'Usuarios activos',
            icon: 'how_to_reg',
            value: activeUsers,
            route: null,
          },
          {
            key: 'banks',
            label: 'Bancos',
            icon: 'account_balance',
            value: banks.length,
            route: '/app/banks',
          },
          {
            key: 'clients',
            label: 'Clientes',
            icon: 'people',
            value: clients.length,
            route: '/app/clients',
          },
          { key: 'cases', label: 'Casos', icon: 'work', value: cases.length, route: '/app/cases' },
          {
            key: 'activeCases',
            label: 'Casos activos',
            icon: 'pending_actions',
            value: activeCases,
            route: null,
          },
          {
            key: 'successCases',
            label: 'Casos éxito',
            icon: 'verified',
            value: successCases,
            route: null,
          },
        ]);
      },
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  open(metric: SuperadminMetric): void {
    if (metric.route) {
      this.router.navigateByUrl(metric.route);
    }
  }
}
