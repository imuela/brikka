import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { OPERATION_TYPE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { OPERATION_TYPES } from '../case.model';
import { CasesService } from '../cases.service';

/**
 * Create (/app/cases/new) and edit (/app/cases/:id/edit) share this component. `operationType` is
 * the only field either endpoint accepts (CreateCaseApiRequest/UpdateCaseApiRequest). Sprint 20
 * (ADR-PROCESS-008): populated from OPERATION_TYPES — a frontend-only closed catalog approved
 * explicitly by the project owner (the backend field itself stays free text, no CHECK constraint).
 */
@Component({
  selector: 'app-case-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
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
  readonly operationTypes = OPERATION_TYPES;
  readonly operationTypeLabels = OPERATION_TYPE_LABELS;

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
