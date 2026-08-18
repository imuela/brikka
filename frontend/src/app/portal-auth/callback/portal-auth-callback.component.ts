import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { PortalAuthService } from '../portal-auth.service';
import { PortalSessionService } from '../portal-session.service';

@Component({
  selector: 'app-portal-auth-callback',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './portal-auth-callback.component.html',
})
export class PortalAuthCallbackComponent {
  private readonly portalAuthService = inject(PortalAuthService);
  private readonly portalSessionService = inject(PortalSessionService);
  private readonly router = inject(Router);

  readonly errorMessage = signal<string | null>(null);

  constructor() {
    void this.completeLogin();
  }

  private async completeLogin(): Promise<void> {
    try {
      const returnUrl = await this.portalAuthService.handleCallback(window.location.href);
      await this.portalSessionService.hydrate();
      await this.router.navigateByUrl(returnUrl);
    } catch {
      this.errorMessage.set('No se ha podido completar el inicio de sesión. Inténtalo de nuevo.');
    }
  }

  retry(): void {
    void this.router.navigate(['/portal/login']);
  }
}
