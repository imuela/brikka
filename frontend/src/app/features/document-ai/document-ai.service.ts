import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { DocumentAiExtraction } from './document-ai.model';

/** Sprint 33. Thin wrapper over /api/v1/documents/{documentId}/ai/document-extractions. */
@Injectable({ providedIn: 'root' })
export class DocumentAiService {
  private readonly apiClient = inject(ApiClient);

  list(documentId: string): Observable<DocumentAiExtraction[]> {
    return this.apiClient.get<DocumentAiExtraction[]>(
      `/api/v1/documents/${documentId}/ai/document-extractions`,
    );
  }

  analyze(documentId: string, documentVersionId: string): Observable<DocumentAiExtraction> {
    return this.apiClient.post<DocumentAiExtraction>(
      `/api/v1/documents/${documentId}/ai/document-extractions`,
      { documentVersionId },
    );
  }
}
