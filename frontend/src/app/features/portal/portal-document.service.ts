import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { PortalDocument, PortalDocumentRequest } from './portal-document.model';

/** Thin wrapper over PortalDocumentController — GET only ever returns published documents
 * (server-enforced); upload always creates a new document+version (never a "replace", matching
 * the real contract — 07_PORTAL_CLIENTE.md). listDocumentRequests (Sprint 19, ADR-PROCESS-007) is
 * the explicit "Solicitudes de documentación" view, separate from the opportunistic auto-fulfill
 * side effect that upload() already triggers server-side. */
@Injectable({ providedIn: 'root' })
export class PortalDocumentService {
  private readonly apiClient = inject(ApiClient);

  list(caseId: string): Observable<PortalDocument[]> {
    return this.apiClient.get<PortalDocument[]>(`/api/v1/portal/cases/${caseId}/documents`);
  }

  upload(caseId: string, documentTypeId: string, file: File): Observable<PortalDocument> {
    const formData = new FormData();
    formData.append('documentTypeId', documentTypeId);
    formData.append('file', file);
    return this.apiClient.post<PortalDocument>(
      `/api/v1/portal/cases/${caseId}/documents`,
      formData,
    );
  }

  listDocumentRequests(caseId: string): Observable<PortalDocumentRequest[]> {
    return this.apiClient.get<PortalDocumentRequest[]>(
      `/api/v1/portal/cases/${caseId}/document-requests`,
    );
  }
}
