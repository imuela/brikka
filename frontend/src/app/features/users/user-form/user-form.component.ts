import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { SessionStore } from '../../../core/session/session.store';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { Company } from '../../companies/company.model';
import { CompanyService } from '../../companies/company.service';
import { UserService } from '../user.service';

/**
 * Create (/app/users/new) and edit (/app/users/:id/edit) share this component, mirroring
 * CaseFormComponent — but unlike Cases, the two backend operations accept different fields:
 * POST /api/v1/users accepts email/firstName/lastName/role/externalIdentityId(/companyId), while
 * PATCH /api/v1/users/{id} only accepts firstName/lastName (UpdateUserApiRequest). In edit mode
 * the create-only fields are shown disabled (real values, for context) rather than sent-but-
 * ignored or hidden outright — only firstName/lastName ever reach the request body.
 *
 * Role options are deliberately limited to MANAGER/BROKER: SUPERADMIN is rejected unconditionally
 * by UserProvisioningService (the created user's tenant is always the caller's own resolved
 * tenant, and SUPERADMIN must never have a company_id — ADR-IDENTITY-001), and CLIENT is
 * provisioned exclusively through the separate Portal Cliente flow (ClientPortalAccount, Sprint 7)
 * — mixing it into this internal admin screen would blur that security boundary (CLAUDE.md §7).
 *
 * Sprint 28: a GLOBAL SUPERADMIN caller must supply companyId explicitly (the backend has no
 * tenant of its own to fall back to, ADR-RBAC-002 point 3) — this was previously missing from the
 * form entirely (the "Nuevo usuario" action was hidden for SUPERADMIN, even though the backend
 * already supported it since Sprint 27). MANAGER/BROKER never see this field; their own tenant is
 * always resolved server-side and any companyId they sent would be ignored anyway.
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
  private readonly companyService = inject(CompanyService);
  private readonly sessionStore = inject(SessionStore);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly userId = this.route.snapshot.paramMap.get('id');
  readonly isEditMode = this.userId !== null;
  readonly isSuperadmin = this.sessionStore.role() === 'SUPERADMIN';
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly roleOptions = ['MANAGER', 'BROKER'];
  readonly companies = signal<Company[] | null>(null);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    role: ['BROKER', Validators.required],
    externalIdentityId: ['', Validators.required],
    companyId: [''],
  });

  constructor() {
    if (!this.isEditMode && this.isSuperadmin) {
      this.form.controls.companyId.addValidators(Validators.required);
      this.companyService.list().subscribe({
        next: (companies) => this.companies.set(companies),
        error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
      });
    }
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
      : this.userService.create(
          this.isSuperadmin ? value : { ...value, companyId: undefined },
        );

    request$.subscribe({
      next: () => this.router.navigate(['/app/users']),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }
}
