import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../core/http/api-error';
import { friendlyErrorMessage } from '../../core/http/error-messages';
import { LogoComponent } from '../../shared/logo/logo.component';
import { PortalAuthService } from '../portal-auth.service';
import { PortalSessionService } from '../portal-session.service';

@Component({
  selector: 'app-portal-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    LogoComponent,
  ],
  templateUrl: './portal-login.component.html',
})
export class PortalLoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly portalAuthService = inject(PortalAuthService);
  private readonly portalSessionService = inject(PortalSessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    try {
      await this.portalAuthService.login(email, password);
      await this.portalSessionService.hydrate();
      const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/portal';
      await this.router.navigateByUrl(returnUrl);
    } catch (err) {
      this.loading.set(false);
      this.error.set(friendlyErrorMessage(err as ApiError));
    }
  }
}
