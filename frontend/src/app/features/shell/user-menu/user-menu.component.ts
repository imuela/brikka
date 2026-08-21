import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { AuthService } from '../../../auth/auth.service';
import { SessionStore } from '../../../core/session/session.store';
import { ROLE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';

@Component({
  selector: 'app-user-menu',
  standalone: true,
  imports: [MatMenuModule, MatButtonModule, MatIconModule, StatusLabelPipe],
  templateUrl: './user-menu.component.html',
})
export class UserMenuComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  readonly sessionStore = inject(SessionStore);
  readonly roleLabels = ROLE_LABELS;

  /** Sprint 29 (stabilization): clearing the session alone left the SPA rendering the previous
   * authenticated screen until the next HTTP call happened to 401 — clicking "Cerrar sesión" must
   * navigate immediately, the same way a 401-triggered logout already does in errorInterceptor. */
  logout(): void {
    this.authService.logout();
    this.sessionStore.clear();
    void this.router.navigate(['/login']);
  }
}
