import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../core/http/api-client';
import { PortalMeResponse } from './portal-me.model';
import { PortalSessionStore } from './portal-session.store';

/** Orchestrates hydrating PortalSessionStore from GET /api/v1/portal/me — called once, right
 * after a successful Portal login (mirrors SessionService). */
@Injectable({ providedIn: 'root' })
export class PortalSessionService {
  private readonly apiClient = inject(ApiClient);
  private readonly portalSessionStore = inject(PortalSessionStore);

  async hydrate(): Promise<void> {
    const client = await firstValueFrom(
      this.apiClient.get<PortalMeResponse>('/api/v1/portal/me'),
    );
    this.portalSessionStore.setClient(client);
  }

  clear(): void {
    this.portalSessionStore.clear();
  }
}
