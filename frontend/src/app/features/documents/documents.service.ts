import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  CaseDocument,
  CaseDocumentPublication,
  CaseDocumentRequest,
  CaseDocumentVersion,
  CreateCaseDocumentRequest,
  CreateCaseDocumentRequestRequest,
  DocumentType,
  DownloadUrl,
  ReviewCaseDocumentRequest,
  UpdateCaseDocumentRequestRequest,
} from './document.model';

/** Sprint 15: thin wrapper over the real /api/v1/documents, /api/v1/cases/{caseId}/documents,
 * /api/v1/cases/{caseId}/document-requests and /api/v1/document-types contracts
 * (17_API_SPECIFICATION_DETAILED.md §9/§10) — no fields, endpoints or business rules beyond what
 * the backend exposes. document-requirements is deliberately not called anywhere in this service:
 * Sprint 15 scope keeps it fully out (no read, no write), per explicit authorization. */
@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private readonly apiClient = inject(ApiClient);

  listDocumentTypes(): Observable<DocumentType[]> {
    return this.apiClient.get<DocumentType[]>('/api/v1/document-types');
  }

  list(caseId: string): Observable<CaseDocument[]> {
    return this.apiClient.get<CaseDocument[]>(`/api/v1/cases/${caseId}/documents`);
  }

  create(caseId: string, request: CreateCaseDocumentRequest): Observable<CaseDocument> {
    return this.apiClient.post<CaseDocument>(`/api/v1/cases/${caseId}/documents`, request);
  }

  listVersions(id: string): Observable<CaseDocumentVersion[]> {
    return this.apiClient.get<CaseDocumentVersion[]>(`/api/v1/documents/${id}/versions`);
  }

  uploadVersion(id: string, file: File): Observable<CaseDocumentVersion> {
    const formData = new FormData();
    formData.append('file', file);
    return this.apiClient.post<CaseDocumentVersion>(`/api/v1/documents/${id}/versions`, formData);
  }

  review(id: string, request: ReviewCaseDocumentRequest): Observable<CaseDocumentVersion> {
    return this.apiClient.post<CaseDocumentVersion>(`/api/v1/documents/${id}/review`, request);
  }

  publish(id: string): Observable<CaseDocumentPublication> {
    return this.apiClient.post<CaseDocumentPublication>(`/api/v1/documents/${id}/publish`);
  }

  unpublish(id: string): Observable<void> {
    return this.apiClient.post<void>(`/api/v1/documents/${id}/unpublish`);
  }

  downloadCurrent(id: string): Observable<DownloadUrl> {
    return this.apiClient.get<DownloadUrl>(`/api/v1/documents/${id}/download`);
  }

  downloadVersion(id: string, versionId: string): Observable<DownloadUrl> {
    return this.apiClient.get<DownloadUrl>(
      `/api/v1/documents/${id}/versions/${versionId}/download`,
    );
  }

  listRequests(caseId: string): Observable<CaseDocumentRequest[]> {
    return this.apiClient.get<CaseDocumentRequest[]>(
      `/api/v1/cases/${caseId}/document-requests`,
    );
  }

  createRequest(
    caseId: string,
    request: CreateCaseDocumentRequestRequest,
  ): Observable<CaseDocumentRequest> {
    return this.apiClient.post<CaseDocumentRequest>(
      `/api/v1/cases/${caseId}/document-requests`,
      request,
    );
  }

  updateRequest(
    id: string,
    request: UpdateCaseDocumentRequestRequest,
  ): Observable<CaseDocumentRequest> {
    return this.apiClient.patch<CaseDocumentRequest>(`/api/v1/document-requests/${id}`, request);
  }
}
