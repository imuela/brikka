import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { AuthService } from '../../../auth/auth.service';
import { SessionStore } from '../../../core/session/session.store';

@Component({
  selector: 'app-user-menu',
  standalone: true,
  imports: [MatMenuModule, MatButtonModule, MatIconModule],
  templateUrl: './user-menu.component.html',
})
export class UserMenuComponent {
  private readonly authService = inject(AuthService);
  readonly sessionStore = inject(SessionStore);

  logout(): void {
    this.authService.logout();
  }
}
