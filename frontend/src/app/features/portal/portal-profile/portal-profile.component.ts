import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { PortalSessionStore } from '../../../portal-auth/portal-session.store';
import { PortalProfileService } from '../portal-profile.service';

/** PATCH /api/v1/portal/profile only ever accepts email/phone (UpdatePortalProfileApiRequest) —
 * name is never editable from the Portal. */
@Component({
  selector: 'app-portal-profile',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './portal-profile.component.html',
})
export class PortalProfileComponent {
  private readonly fb = inject(FormBuilder);
  private readonly portalProfileService = inject(PortalProfileService);
  readonly portalSessionStore = inject(PortalSessionStore);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly saved = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: [this.portalSessionStore.client()?.email ?? '', [Validators.required, Validators.email]],
    phone: [this.portalSessionStore.client()?.phone ?? '', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.saved.set(false);
    this.portalProfileService.update(this.form.getRawValue()).subscribe({
      next: (me) => {
        this.loading.set(false);
        this.saved.set(true);
        this.portalSessionStore.setClient(me);
      },
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }
}
