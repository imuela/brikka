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
import { AuthService } from '../auth.service';

/**
 * Sprint 22 authorization Fase 5: single route, two modes selected by the presence of `?token=`.
 * The request step always shows the same generic confirmation regardless of whether the email
 * matched anything (§11, "no revelar si el usuario existe") — the backend already guarantees this
 * at the API level; this component must not undermine it by branching on the response.
 */
@Component({
  selector: 'app-password-reset',
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
  templateUrl: './password-reset.component.html',
})
export class PasswordResetComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly token = this.route.snapshot.queryParamMap.get('token');
  readonly isConfirmMode = this.token !== null;

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly requestSubmitted = signal(false);

  readonly requestForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  readonly confirmForm = this.fb.nonNullable.group({
    newPassword: ['', Validators.required],
  });

  readonly confirmSucceeded = signal(false);

  async submitRequest(): Promise<void> {
    if (this.requestForm.invalid) {
      this.requestForm.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      await this.authService.requestPasswordReset(this.requestForm.getRawValue().email);
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
      await this.authService.confirmPasswordReset(
        this.token,
        this.confirmForm.getRawValue().newPassword,
      );
      this.confirmSucceeded.set(true);
      setTimeout(() => void this.router.navigateByUrl('/login'), 2000);
    } catch (err) {
      this.loading.set(false);
      this.error.set(friendlyErrorMessage(err as ApiError));
    }
  }
}
