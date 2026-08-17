import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { ApiClient } from '../http/api-client';
import { MeResponse, PermissionsResponse } from './me.model';
import { SessionStore } from './session.store';

/** Orchestrates hydrating SessionStore from GET /me + GET /me/permissions
 * (17_API_SPECIFICATION_DETAILED.md §4) — called once, right after a successful login. */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly apiClient = inject(ApiClient);
  private readonly sessionStore = inject(SessionStore);

  async hydrate(): Promise<void> {
    const [user, permissions] = await Promise.all([
      firstValueFrom(this.apiClient.get<MeResponse>('/api/v1/me')),
      firstValueFrom(this.apiClient.get<PermissionsResponse>('/api/v1/me/permissions')),
    ]);
    this.sessionStore.setUser(user);
    this.sessionStore.setPermissions(permissions.permissions);
  }

  clear(): void {
    this.sessionStore.clear();
  }
}
