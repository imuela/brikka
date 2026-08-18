import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CaseDocumentVersion } from '../document.model';
import { DocumentsService } from '../documents.service';

export interface ReviewDocumentDialogData {
  documentId: string;
}

/** decision is APPROVED/REJECTED only — "solicitar nueva versión" is modeled as REJECTED with an
 * explanatory comment, matching the backend contract exactly (no third decision value invented). */
@Component({
  selector: 'app-review-document-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './review-document-dialog.component.html',
})
export class ReviewDocumentDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly documentsService = inject(DocumentsService);
  private readonly dialogRef = inject(
    MatDialogRef<ReviewDocumentDialogComponent, CaseDocumentVersion>,
  );
  private readonly data = inject<ReviewDocumentDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    decision: ['', Validators.required],
    comment: [''],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.documentsService.review(this.data.documentId, this.form.getRawValue()).subscribe({
      next: (version) => this.dialogRef.close(version),
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
