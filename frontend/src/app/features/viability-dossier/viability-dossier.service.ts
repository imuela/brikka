import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  CaseNarrative,
  GeneratedDocument,
  GeneratedDocumentVersion,
} from './viability-dossier.model';

/** Sprint 32 · BRIKKA V2 I5. Thin wrapper over /api/v1/cases/{caseId}/dossier and its
 * read-only deterministic narrative. */
@Injectable({ providedIn: 'root' })
export class ViabilityDossierService {
  private readonly apiClient = inject(ApiClient);

  get(caseId: string): Observable<GeneratedDocument> {
    return this.apiClient.get<GeneratedDocument>(`/api/v1/cases/${caseId}/dossier`);
  }

  generate(caseId: string): Observable<GeneratedDocumentVersion> {
    return this.apiClient.post<GeneratedDocumentVersion>(`/api/v1/cases/${caseId}/dossier`);
  }

  getNarrative(caseId: string): Observable<CaseNarrative> {
    return this.apiClient.get<CaseNarrative>(`/api/v1/cases/${caseId}/dossier/narrative`);
  }
}
