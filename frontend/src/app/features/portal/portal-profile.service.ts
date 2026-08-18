import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { PortalMeResponse, UpdatePortalProfileRequest } from '../../portal-auth/portal-me.model';

/** Thin wrapper over PortalProfileController — PATCH /api/v1/portal/profile only ever accepts
 * email/phone (UpdatePortalProfileApiRequest); nothing else is editable. */
@Injectable({ providedIn: 'root' })
export class PortalProfileService {
  private readonly apiClient = inject(ApiClient);

  update(request: UpdatePortalProfileRequest): Observable<PortalMeResponse> {
    return this.apiClient.patch<PortalMeResponse>('/api/v1/portal/profile', request);
  }
}
