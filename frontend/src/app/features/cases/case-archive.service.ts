import { HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';

/**
 * BRIKKA V2 I5. Downloads the case documents ZIP from
 * GET /api/v1/cases/{caseId}/documents/archive. The response is the binary stream itself (not a
 * presigned URL), so it must go through HttpClient to carry the bearer token.
 */
@Injectable({ providedIn: 'root' })
export class CaseArchiveService {
  private readonly apiClient = inject(ApiClient);

  downloadArchive(caseId: string): Observable<HttpResponse<Blob>> {
    return this.apiClient.getBlob(`/api/v1/cases/${caseId}/documents/archive`);
  }
}
