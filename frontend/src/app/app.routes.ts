import { Routes } from '@angular/router';

import { authGuard } from './auth/auth.guard';
import { permissionGuard } from './auth/permission.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'app' },
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'auth/callback',
    loadComponent: () =>
      import('./auth/callback/auth-callback.component').then((m) => m.AuthCallbackComponent),
  },
  {
    path: 'app',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/shell/shell.component').then((m) => m.ShellComponent),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/shell/dashboard/dashboard.component').then(
            (m) => m.DashboardComponent,
          ),
      },
      {
        path: 'forbidden',
        loadComponent: () =>
          import('./features/shell/forbidden/forbidden.component').then(
            (m) => m.ForbiddenComponent,
          ),
      },
      // Sprint 14: Clientes (CRM). "new" must precede ":id" so it is not matched as an id param.
      {
        path: 'clients',
        canActivate: [permissionGuard],
        data: { permission: 'CLIENT_READ' },
        loadComponent: () =>
          import('./features/clients/client-list/client-list.component').then(
            (m) => m.ClientListComponent,
          ),
      },
      {
        path: 'clients/new',
        canActivate: [permissionGuard],
        data: { permission: 'CLIENT_CREATE' },
        loadComponent: () =>
          import('./features/clients/client-form/client-form.component').then(
            (m) => m.ClientFormComponent,
          ),
      },
      {
        path: 'clients/:id/edit',
        canActivate: [permissionGuard],
        data: { permission: 'CLIENT_UPDATE' },
        loadComponent: () =>
          import('./features/clients/client-form/client-form.component').then(
            (m) => m.ClientFormComponent,
          ),
      },
      {
        path: 'clients/:id',
        canActivate: [permissionGuard],
        data: { permission: 'CLIENT_READ' },
        loadComponent: () =>
          import('./features/clients/client-detail/client-detail.component').then(
            (m) => m.ClientDetailComponent,
          ),
      },
      // Sprint 14: Casos (Operaciones). Same "new"-before-":id" ordering rule applies.
      {
        path: 'cases',
        canActivate: [permissionGuard],
        data: { permission: 'CASE_READ' },
        loadComponent: () =>
          import('./features/cases/case-list/case-list.component').then(
            (m) => m.CaseListComponent,
          ),
      },
      {
        path: 'cases/new',
        canActivate: [permissionGuard],
        data: { permission: 'CASE_CREATE' },
        loadComponent: () =>
          import('./features/cases/case-form/case-form.component').then(
            (m) => m.CaseFormComponent,
          ),
      },
      {
        path: 'cases/:id/edit',
        canActivate: [permissionGuard],
        data: { permission: 'CASE_UPDATE' },
        loadComponent: () =>
          import('./features/cases/case-form/case-form.component').then(
            (m) => m.CaseFormComponent,
          ),
      },
      {
        path: 'cases/:id',
        canActivate: [permissionGuard],
        data: { permission: 'CASE_READ' },
        loadComponent: () =>
          import('./features/cases/case-detail/case-detail.component').then(
            (m) => m.CaseDetailComponent,
          ),
      },
      // Sprint 16: Bancos (catálogo global). "new" must precede ":id" so it is not matched as an
      // id param — same rule as clients/cases above.
      {
        path: 'banks',
        canActivate: [permissionGuard],
        data: { permission: 'BANK_READ' },
        loadComponent: () =>
          import('./features/banks/bank-list/bank-list.component').then(
            (m) => m.BankListComponent,
          ),
      },
      {
        path: 'banks/:id',
        canActivate: [permissionGuard],
        data: { permission: 'BANK_READ' },
        loadComponent: () =>
          import('./features/banks/bank-detail/bank-detail.component').then(
            (m) => m.BankDetailComponent,
          ),
      },
    ],
  },
  { path: '**', redirectTo: 'app' },
];
