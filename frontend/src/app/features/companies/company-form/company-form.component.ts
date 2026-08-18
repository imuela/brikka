import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CompanyService } from '../company.service';

/** Create (/app/companies/new, SUPERADMIN-only via COMPANY_CREATE) and edit
 * (/app/companies/:id/edit) share this component, mirroring CaseFormComponent. Both operations
 * accept the same three fields (legalName/tradeName/taxId) — status is never editable here, it
 * only changes through the dedicated suspend/delete actions on the detail screen. */
@Component({
  selector: 'app-company-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './company-form.component.html',
})
export class CompanyFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly companyService = inject(CompanyService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly companyId = this.route.snapshot.paramMap.get('id');
  readonly isEditMode = this.companyId !== null;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    legalName: ['', Validators.required],
    tradeName: ['', Validators.required],
    taxId: ['', Validators.required],
  });

  constructor() {
    if (this.companyId) {
      this.companyService.get(this.companyId).subscribe({
        next: (company) =>
          this.form.patchValue({
            legalName: company.legalName,
            tradeName: company.tradeName,
            taxId: company.taxId,
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
    const request$ = this.companyId
      ? this.companyService.update(this.companyId, value)
      : this.companyService.create(value);

    request$.subscribe({
      next: (company) => this.router.navigate(['/app/companies', company.id]),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }
}
