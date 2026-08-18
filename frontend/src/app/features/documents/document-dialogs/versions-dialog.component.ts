import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { REVIEW_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { CaseDocumentVersion } from '../document.model';
import { DocumentsService } from '../documents.service';

export interface VersionsDialogData {
  documentId: string;
}

/** Read-only: lists every version of a document and lets the user download any of them via a
 * short-lived presigned URL (never the raw storage key). */
@Component({
  selector: 'app-versions-dialog',
  standalone: true,
  imports: [
    DatePipe,
    MatDialogModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './versions-dialog.component.html',
})
export class VersionsDialogComponent {
  private readonly documentsService = inject(DocumentsService);
  private readonly dialogRef = inject(MatDialogRef<VersionsDialogComponent, void>);
  private readonly data = inject<VersionsDialogData>(MAT_DIALOG_DATA);

  readonly versions = signal<CaseDocumentVersion[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly columns = ['versionNumber', 'originalFilename', 'reviewStatus', 'uploadedAt', 'actions'];
  readonly reviewStatusLabels = REVIEW_STATUS_LABELS;

  constructor() {
    this.documentsService.listVersions(this.data.documentId).subscribe({
      next: (versions) => this.versions.set(versions),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  download(versionId: string): void {
    this.documentsService.downloadVersion(this.data.documentId, versionId).subscribe({
      next: (download) => window.open(download.url, '_blank'),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  close(): void {
    this.dialogRef.close();
  }
}
