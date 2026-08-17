import { Routes } from '@angular/router';

import { authGuard } from './auth/auth.guard';

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
      // Sprint 14+ feature routes are added here, each with its own `data: { permission: '...' }`
      // guarded by permissionGuard — none exist yet (Sprint 13 scope).
    ],
  },
  { path: '**', redirectTo: 'app' },
];
