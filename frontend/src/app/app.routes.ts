import { Routes } from '@angular/router';

import { authGuard } from './auth/auth.guard';
import { permissionGuard } from './auth/permission.guard';
import { portalAuthGuard } from './portal-auth/portal-auth.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'app' },
  {
    path: 'login',
    loadComponent: () => import('./auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'password-reset',
    loadComponent: () =>
      import('./auth/password-reset/password-reset.component').then(
        (m) => m.PasswordResetComponent,
      ),
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
      // Sprint 17: Tareas (tenant-wide task inbox, GET /api/v1/tasks) y Notificaciones (siempre
      // las propias del usuario autenticado, GET /api/v1/notifications) — sin sub-rutas de
      // detalle, todo el detalle/edición vive en diálogos, igual que Bancos.
      {
        path: 'tasks',
        canActivate: [permissionGuard],
        data: { permission: 'TASK_READ' },
        loadComponent: () =>
          import('./features/tasks/task-list/task-list.component').then(
            (m) => m.TaskListComponent,
          ),
      },
      {
        path: 'notifications',
        canActivate: [permissionGuard],
        data: { permission: 'NOTIFICATION_READ' },
        loadComponent: () =>
          import('./features/notifications/notification-list/notification-list.component').then(
            (m) => m.NotificationListComponent,
          ),
      },
      // Sprint 18: Usuarios (tenant-wide, GET /api/v1/users — SUPERADMIN always 403s here without
      // a SUPPORT_SESSION, not worked around), Empresas (GLOBAL for SUPERADMIN, own company only
      // for MANAGER) and Planes (SUPERADMIN-only global catalog). "new" precedes ":id"/":id/edit"
      // for the same routing reason as clients/cases/banks above.
      {
        path: 'users',
        canActivate: [permissionGuard],
        data: { permission: 'USER_READ' },
        loadComponent: () =>
          import('./features/users/user-list/user-list.component').then(
            (m) => m.UserListComponent,
          ),
      },
      {
        path: 'users/new',
        canActivate: [permissionGuard],
        data: { permission: 'USER_CREATE' },
        loadComponent: () =>
          import('./features/users/user-form/user-form.component').then(
            (m) => m.UserFormComponent,
          ),
      },
      {
        path: 'users/:id/edit',
        canActivate: [permissionGuard],
        data: { permission: 'USER_UPDATE' },
        loadComponent: () =>
          import('./features/users/user-form/user-form.component').then(
            (m) => m.UserFormComponent,
          ),
      },
      {
        path: 'companies',
        canActivate: [permissionGuard],
        data: { permission: 'COMPANY_READ' },
        loadComponent: () =>
          import('./features/companies/company-list/company-list.component').then(
            (m) => m.CompanyListComponent,
          ),
      },
      {
        path: 'companies/new',
        canActivate: [permissionGuard],
        data: { permission: 'COMPANY_CREATE' },
        loadComponent: () =>
          import('./features/companies/company-form/company-form.component').then(
            (m) => m.CompanyFormComponent,
          ),
      },
      {
        path: 'companies/:id/edit',
        canActivate: [permissionGuard],
        data: { permission: 'COMPANY_UPDATE' },
        loadComponent: () =>
          import('./features/companies/company-form/company-form.component').then(
            (m) => m.CompanyFormComponent,
          ),
      },
      {
        path: 'companies/:id',
        canActivate: [permissionGuard],
        data: { permission: 'COMPANY_READ' },
        loadComponent: () =>
          import('./features/companies/company-detail/company-detail.component').then(
            (m) => m.CompanyDetailComponent,
          ),
      },
      {
        path: 'plans',
        canActivate: [permissionGuard],
        data: { permission: 'PLAN_READ' },
        loadComponent: () =>
          import('./features/plans/plan-list/plan-list.component').then(
            (m) => m.PlanListComponent,
          ),
      },
    ],
  },
  // Sprint 19 (ADR-PROCESS-007): Portal Cliente — a fully separate route subtree, own guard
  // (portalAuthGuard, never authGuard), own token issuer (Brika's own Portal-issued JWTs, Sprint
  // 22 cierre — physically separate from the internal issuer, same separation Keycloak's two
  // realms used to provide). "cases/:id" needs no
  // "new"/":id/edit" siblings: Portal never creates or edits a case, only reads it and its
  // embedded documents/document-requests/messages (all read/write via the sections inside
  // PortalCaseDetailComponent, not separate routes).
  {
    path: 'portal/login',
    loadComponent: () =>
      import('./portal-auth/login/portal-login.component').then((m) => m.PortalLoginComponent),
  },
  {
    path: 'portal/password-reset',
    loadComponent: () =>
      import('./portal-auth/password-reset/portal-password-reset.component').then(
        (m) => m.PortalPasswordResetComponent,
      ),
  },
  {
    path: 'portal',
    canActivate: [portalAuthGuard],
    loadComponent: () =>
      import('./features/portal/portal-shell/portal-shell.component').then(
        (m) => m.PortalShellComponent,
      ),
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/portal/portal-dashboard/portal-dashboard.component').then(
            (m) => m.PortalDashboardComponent,
          ),
      },
      {
        path: 'cases/:id',
        loadComponent: () =>
          import('./features/portal/portal-case-detail/portal-case-detail.component').then(
            (m) => m.PortalCaseDetailComponent,
          ),
      },
      {
        path: 'profile',
        loadComponent: () =>
          import('./features/portal/portal-profile/portal-profile.component').then(
            (m) => m.PortalProfileComponent,
          ),
      },
    ],
  },
  { path: '**', redirectTo: 'app' },
];
