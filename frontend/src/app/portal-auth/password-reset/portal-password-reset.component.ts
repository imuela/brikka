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

/** Portal Cliente counterpart of PasswordResetComponent — see its javadoc-equivalent comment. */
@Component({
  selector: 'app-portal-password-reset',
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
  templateUrl: './portal-password-reset.component.html',
})
export class PortalPasswordResetComponent {
  private readonly fb = inject(FormBuilder);
  private readonly portalAuthService = inject(PortalAuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly token = this.route.snapshot.queryParamMap.get('token');
  readonly isConfirmMode = this.token !== null;

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly requestSubmitted = signal(false);
  readonly confirmSucceeded = signal(false);

  readonly requestForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  readonly confirmForm = this.fb.nonNullable.group({
    newPassword: ['', Validators.required],
  });

  async submitRequest(): Promise<void> {
    if (this.requestForm.invalid) {
      this.requestForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.portalAuthService.requestPasswordReset(this.requestForm.getRawValue().email);
      this.requestSubmitted.set(true);
    } catch (err) {
      this.error.set(friendlyErrorMessage(err as ApiError));
    } finally {
      this.loading.set(false);
    }
  }

  async submitConfirm(): Promise<void> {
    if (this.confirmForm.invalid || !this.token) {
      this.confirmForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.portalAuthService.confirmPasswordReset(
        this.token,
        this.confirmForm.getRawValue().newPassword,
      );
      this.confirmSucceeded.set(true);
      setTimeout(() => void this.router.navigateByUrl('/portal/login'), 2000);
    } catch (err) {
      this.loading.set(false);
      this.error.set(friendlyErrorMessage(err as ApiError));
    }
  }
}
