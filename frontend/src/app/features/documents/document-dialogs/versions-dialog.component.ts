import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { DOCUMENT_AI_STATUS_LABELS, REVIEW_STATUS_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { StatusBadgeComponent } from '../../../shared/status-badge/status-badge.component';
import { DocumentAiExtractedData, DocumentAiExtraction, DocumentAiField } from '../../document-ai/document-ai.model';
import { DocumentAiService } from '../../document-ai/document-ai.service';
import { CaseDocumentVersion } from '../document.model';
import { DocumentsService } from '../documents.service';

export interface VersionsDialogData {
  documentId: string;
}

/** Read-only: lists every version of a document and lets the user download any of them via a
 * short-lived presigned URL (never the raw storage key). Sprint 33: also the "Analizar con IA"
 * entry point per 21_AI_V1_SCOPE.md — one flat list of every analysis run for the document (across
 * all its versions, oldest-analysis-first as the backend returns), never grouped/merged into the
 * version rows above, since an analysis belongs to one specific version forever. */
@Component({
  selector: 'app-versions-dialog',
  standalone: true,
  imports: [
    DatePipe,
    DecimalPipe,
    MatDialogModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
    StatusBadgeComponent,
    HasPermissionDirective,
  ],
  templateUrl: './versions-dialog.component.html',
})
export class VersionsDialogComponent {
  private readonly documentsService = inject(DocumentsService);
  private readonly documentAiService = inject(DocumentAiService);
  private readonly dialogRef = inject(MatDialogRef<VersionsDialogComponent, void>);
  private readonly data = inject<VersionsDialogData>(MAT_DIALOG_DATA);

  readonly versions = signal<CaseDocumentVersion[] | null>(null);
  readonly error = signal<string | null>(null);
  readonly columns = ['versionNumber', 'originalFilename', 'reviewStatus', 'uploadedAt', 'actions'];
  readonly reviewStatusLabels = REVIEW_STATUS_LABELS;

  readonly extractions = signal<DocumentAiExtraction[] | null>(null);
  readonly analyzing = signal<string | null>(null); // versionId currently being analyzed, if any
  readonly aiError = signal<string | null>(null);
  readonly documentAiStatusLabels = DOCUMENT_AI_STATUS_LABELS;

  constructor() {
    this.documentsService.listVersions(this.data.documentId).subscribe({
      next: (versions) => this.versions.set(versions),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    this.loadExtractions();
  }

  private loadExtractions(): void {
    this.documentAiService.list(this.data.documentId).subscribe({
      next: (extractions) => this.extractions.set(extractions),
      error: (err: ApiError) => this.aiError.set(friendlyErrorMessage(err)),
    });
  }

  analyze(versionId: string): void {
    this.analyzing.set(versionId);
    this.aiError.set(null);
    this.documentAiService.analyze(this.data.documentId, versionId).subscribe({
      next: () => {
        this.analyzing.set(null);
        this.loadExtractions();
      },
      error: (err: ApiError) => {
        this.analyzing.set(null);
        this.aiError.set(friendlyErrorMessage(err));
      },
    });
  }

  extractionData(extraction: DocumentAiExtraction): DocumentAiExtractedData | null {
    const raw = extraction.extractedData;
    if (raw && !Array.isArray(raw)) {
      return raw as DocumentAiExtractedData;
    }
    return null;
  }

  extractionFields(extraction: DocumentAiExtraction): DocumentAiField[] {
    const raw = extraction.extractedData;
    if (Array.isArray(raw)) {
      return raw;
    }
    return this.extractionData(extraction)?.fields ?? [];
  }

  versionNumber(versionId: string): number | null {
    return this.versions()?.find((v) => v.id === versionId)?.versionNumber ?? null;
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
