import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { UserService } from '../user.service';

/**
 * Create (/app/users/new) and edit (/app/users/:id/edit) share this component, mirroring
 * CaseFormComponent — but unlike Cases, the two backend operations accept different fields:
 * POST /api/v1/users accepts email/firstName/lastName/role/externalIdentityId, while
 * PATCH /api/v1/users/{id} only accepts firstName/lastName (UpdateUserApiRequest). In edit mode
 * the create-only fields are shown disabled (real values, for context) rather than sent-but-
 * ignored or hidden outright — only firstName/lastName ever reach the request body.
 *
 * Role options are deliberately limited to MANAGER/BROKER: SUPERADMIN is rejected unconditionally
 * by UserProvisioningService (the created user's tenant is always the caller's own resolved
 * tenant, and SUPERADMIN must never have a company_id — ADR-IDENTITY-001), and CLIENT is
 * provisioned exclusively through the separate Portal Cliente flow (ClientPortalAccount, Sprint 7)
 * — mixing it into this internal admin screen would blur that security boundary (CLAUDE.md §7).
 */
@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './user-form.component.html',
})
export class UserFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly userService = inject(UserService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly userId = this.route.snapshot.paramMap.get('id');
  readonly isEditMode = this.userId !== null;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly roleOptions = ['MANAGER', 'BROKER'];

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    role: ['BROKER', Validators.required],
    externalIdentityId: ['', Validators.required],
  });

  constructor() {
    if (this.userId) {
      this.form.controls.email.disable();
      this.form.controls.role.disable();
      this.form.controls.externalIdentityId.disable();
      this.userService.get(this.userId).subscribe({
        next: (user) =>
          this.form.patchValue({
            email: user.email,
            firstName: user.firstName,
            lastName: user.lastName,
            role: user.role,
            externalIdentityId: '',
          }),
        error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
      });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();

    const request$ = this.userId
      ? this.userService.update(this.userId, {
          firstName: value.firstName,
          lastName: value.lastName,
        })
      : this.userService.create(value);

    request$.subscribe({
      next: () => this.router.navigate(['/app/users']),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }
}
