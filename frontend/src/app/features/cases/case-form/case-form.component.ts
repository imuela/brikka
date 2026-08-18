import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CasesService } from '../cases.service';

/**
 * Create (/app/cases/new) and edit (/app/cases/:id/edit) share this component. `operationType` is
 * the only field either endpoint accepts (CreateCaseApiRequest/UpdateCaseApiRequest) — free text,
 * no catalog documented anywhere (backend javadoc), so no dropdown is invented here.
 */
@Component({
  selector: 'app-case-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './case-form.component.html',
  styleUrl: './case-form.component.scss',
})
export class CaseFormComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly caseId = this.route.snapshot.paramMap.get('id');
  readonly isEditMode = this.caseId !== null;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    operationType: ['', Validators.required],
  });

  constructor() {
    if (this.caseId) {
      this.casesService.get(this.caseId).subscribe({
        next: (theCase) => this.form.patchValue({ operationType: theCase.operationType }),
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
    const request$ = this.caseId
      ? this.casesService.update(this.caseId, value)
      : this.casesService.create(value);

    request$.subscribe({
      next: (theCase) => this.router.navigate(['/app/cases', theCase.id]),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }
}
