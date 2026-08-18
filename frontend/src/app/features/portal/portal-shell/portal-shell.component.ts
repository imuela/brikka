import { Component, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';

import { PortalAuthService } from '../../../portal-auth/portal-auth.service';
import { PortalSessionStore } from '../../../portal-auth/portal-session.store';

/** Portal Cliente shell — deliberately a plain toolbar, not the internal ShellComponent's
 * sidenav: the Portal surface only ever has 2-3 destinations (Sprint 19, ADR-PROCESS-007), so a
 * collapsible sidenav would be over-engineering. Never imports anything from features/shell/ —
 * the two surfaces share zero UI components, only design tokens (global styles). */
@Component({
  selector: 'app-portal-shell',
  standalone: true,
  imports: [RouterLink, RouterOutlet, MatToolbarModule, MatButtonModule, MatIconModule],
  templateUrl: './portal-shell.component.html',
  styleUrl: './portal-shell.component.scss',
})
export class PortalShellComponent {
  private readonly portalAuthService = inject(PortalAuthService);
  readonly portalSessionStore = inject(PortalSessionStore);

  logout(): void {
    this.portalAuthService.logout();
  }
}
