import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AuthService } from '../auth.service';
import { SessionService } from '../../core/session/session.service';

@Component({
  selector: 'app-auth-callback',
  standalone: true,
  imports: [MatCardModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './auth-callback.component.html',
  styleUrl: './auth-callback.component.scss',
})
export class AuthCallbackComponent {
  private readonly authService = inject(AuthService);
  private readonly sessionService = inject(SessionService);
  private readonly router = inject(Router);

  readonly errorMessage = signal<string | null>(null);

  constructor() {
    void this.completeLogin();
  }

  private async completeLogin(): Promise<void> {
    try {
      const returnUrl = await this.authService.handleCallback(window.location.href);
      await this.sessionService.hydrate();
      await this.router.navigateByUrl(returnUrl);
    } catch (error) {
      this.errorMessage.set(error instanceof Error ? error.message : 'Error de autenticación.');
    }
  }

  retry(): void {
    void this.router.navigate(['/login']);
  }
}
