import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';

import { ApiError } from '../../../core/http/api-error';
import { CaseDocument, DocumentType } from '../document.model';
import { DocumentsService } from '../documents.service';

export interface CreateDocumentDialogData {
  caseId: string;
  documentTypes: DocumentType[];
}

@Component({
  selector: 'app-create-document-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './create-document-dialog.component.html',
})
export class CreateDocumentDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly documentsService = inject(DocumentsService);
  private readonly dialogRef = inject(MatDialogRef<CreateDocumentDialogComponent, CaseDocument>);
  private readonly data = inject<CreateDocumentDialogData>(MAT_DIALOG_DATA);

  readonly documentTypes = this.data.documentTypes;
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    documentTypeId: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.documentsService.create(this.data.caseId, this.form.getRawValue()).subscribe({
      next: (document) => this.dialogRef.close(document),
      error: (err: ApiError) => {
        this.loading.set(false);
        this.error.set(err.message);
      },
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
