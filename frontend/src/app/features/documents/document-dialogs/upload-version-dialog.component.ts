import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { CaseDocumentVersion } from '../document.model';
import { DocumentsService } from '../documents.service';

export interface UploadVersionDialogData {
  documentId: string;
}

/** Native <input type="file"> instead of a reactive form control — Angular reactive forms do not
 * bind File objects usefully; the selected File is held in a plain signal instead. */
@Component({
  selector: 'app-upload-version-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatProgressSpinnerModule],
  templateUrl: './upload-version-dialog.component.html',
})
export class UploadVersionDialogComponent {
  private readonly documentsService = inject(DocumentsService);
  private readonly dialogRef = inject(
    MatDialogRef<UploadVersionDialogComponent, CaseDocumentVersion>,
  );
  private readonly data = inject<UploadVersionDialogData>(MAT_DIALOG_DATA);

  readonly file = signal<File | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
  }

  submit(): void {
    const selected = this.file();
    if (!selected) {
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.documentsService.uploadVersion(this.data.documentId, selected).subscribe({
      next: (version) => this.dialogRef.close(version),
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
