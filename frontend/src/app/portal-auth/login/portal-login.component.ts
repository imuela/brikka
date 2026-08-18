import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';

import { PortalAuthService } from '../portal-auth.service';

@Component({
  selector: 'app-portal-login',
  standalone: true,
  imports: [MatCardModule, MatButtonModule],
  templateUrl: './portal-login.component.html',
})
export class PortalLoginComponent {
  private readonly portalAuthService = inject(PortalAuthService);
  private readonly route = inject(ActivatedRoute);

  signIn(): void {
    const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/portal';
    void this.portalAuthService.login(returnUrl);
  }
}
