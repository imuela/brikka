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
import { CaseClient } from '../../cases/case.model';
import { CasesService } from '../../cases/cases.service';
import { CaseDocumentRequest, DocumentType } from '../document.model';
import { DocumentsService } from '../documents.service';

export interface CreateDocumentRequestDialogData {
  caseId: string;
  documentTypes: DocumentType[];
}

/** Reuses CasesService.listClients (features/cases) to offer only clients already linked to this
 * case — no duplicate HTTP call, no new "shared" abstraction for a single reuse (same reasoning as
 * Sprint 14's add-client-dialog reusing ClientsService). requirementId is always null: linking a
 * request to a document-requirements catalog entry is explicitly out of Sprint 15 scope. */
@Component({
  selector: 'app-create-document-request-dialog',
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
  templateUrl: './create-document-request-dialog.component.html',
})
export class CreateDocumentRequestDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly documentsService = inject(DocumentsService);
  private readonly casesService = inject(CasesService);
  private readonly dialogRef = inject(
    MatDialogRef<CreateDocumentRequestDialogComponent, CaseDocumentRequest>,
  );
  private readonly data = inject<CreateDocumentRequestDialogData>(MAT_DIALOG_DATA);

  readonly documentTypes = this.data.documentTypes;
  readonly caseClients = signal<CaseClient[] | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    documentTypeId: ['', Validators.required],
    requestedFromClientId: [''],
    dueAt: [''],
  });

  constructor() {
    this.casesService.listClients(this.data.caseId).subscribe({
      next: (clients) => this.caseClients.set(clients),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    const value = this.form.getRawValue();
    this.documentsService
      .createRequest(this.data.caseId, {
        documentTypeId: value.documentTypeId,
        requestedFromClientId: value.requestedFromClientId || null,
        dueAt: value.dueAt ? new Date(value.dueAt).toISOString() : null,
        requirementId: null,
      })
      .subscribe({
        next: (request) => this.dialogRef.close(request),
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
