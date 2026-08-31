import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { SessionStore } from '../../../core/session/session.store';
import { CASE_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { CASE_STATUSES, Case } from '../case.model';
import { CasesService } from '../cases.service';

export interface ChangeStatusDialogData {
  caseId: string;
}

@Component({
  selector: 'app-change-status-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatCheckboxModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './change-status-dialog.component.html',
})
export class ChangeStatusDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly casesService = inject(CasesService);
  private readonly sessionStore = inject(SessionStore);
  private readonly dialogRef = inject(MatDialogRef<ChangeStatusDialogComponent, Case>);
  private readonly data = inject<ChangeStatusDialogData>(MAT_DIALOG_DATA);

  readonly statuses = CASE_STATUSES;
  readonly caseStatusLabels = CASE_STATUS_LABELS;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  /** BRIKKA V2 I3: only offer the "force" option to users who can actually use it. */
  readonly canOverride = this.sessionStore.hasPermission('CASE_TRANSITION_OVERRIDE');

  readonly form = this.fb.nonNullable.group({
    newStatus: ['', Validators.required],
    reason: [''],
    override: [false],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.override && !value.reason.trim()) {
      // BRIKKA V2 I3: a forced transition needs a reason (the backend rejects it otherwise).
      this.form.controls.reason.setErrors({ required: true });
      this.form.controls.reason.markAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.casesService.changeStatus(this.data.caseId, value).subscribe({
      next: (theCase) => this.dialogRef.close(theCase),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(friendlyErrorMessage(err));
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
