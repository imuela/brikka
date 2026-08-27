import { Component, computed, inject, signal } from '@angular/core';
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
import { UserService } from '../../../users/user.service';

interface ManagerMetric {
  key: string;
  label: string;
  icon: string;
  value: number;
  route: string | null;
}

/** Sprint 40.x: dedicated MANAGER home — a company-scoped overview grid, distinct from both the
 * SUPERADMIN global grid and the BROKER/legacy tenant dashboard (DashboardComponent), which keep
 * their own unchanged views. Every number here comes from the existing list endpoints
 * (users/clients/cases/banks) — each already scopes to the caller's own tenant server-side for a
 * MANAGER (UserController/ClientController/CaseController's non-superadmin branch), except Banks,
 * which is a global catalog with no company column at all (Bank.java). No new backend endpoint was
 * introduced; the 3 derived metrics (active cases, success cases, active users) are computed
 * client-side from fields the backend already returns, reusing the same case-status semantics as
 * DashboardRepository (active = status NOT IN (COMPLETED, CANCELLED); "éxito" = COMPLETED, the
 * only non-cancelled terminal state — CaseStatus has no separate named "success" concept). */
@Component({
  selector: 'app-manager-dashboard',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './manager-dashboard.component.html',
  styleUrl: './manager-dashboard.component.scss',
})
export class ManagerDashboardComponent {
  private readonly userService = inject(UserService);
  private readonly clientsService = inject(ClientsService);
  private readonly casesService = inject(CasesService);
  private readonly bankService = inject(BankService);
  private readonly router = inject(Router);

  readonly metrics = signal<ManagerMetric[] | null>(null);
  readonly error = signal<string | null>(null);

  // Fila 1 (4 columnas): Usuarios, Usuarios activos, Bancos, Clientes.
  readonly row1 = computed(() => this.metrics()?.slice(0, 4) ?? null);
  // Fila 2 (3 columnas): Casos, Casos activos, Casos éxito.
  readonly row2 = computed(() => this.metrics()?.slice(4) ?? null);

  constructor() {
    forkJoin({
      users: this.userService.list(),
      clients: this.clientsService.list(),
      cases: this.casesService.list(),
      banks: this.bankService.list(),
    }).subscribe({
      next: ({ users, clients, cases, banks }) => {
        const activeUsers = users.filter((u) => u.status === 'ACTIVE').length;
        const activeCases = cases.filter(
          (c) => c.status !== 'COMPLETED' && c.status !== 'CANCELLED',
        ).length;
        const successCases = cases.filter((c) => c.status === 'COMPLETED').length;

        this.metrics.set([
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

  open(metric: ManagerMetric): void {
    if (metric.route) {
      this.router.navigateByUrl(metric.route);
    }
  }
}
