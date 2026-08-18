import { Injectable, computed, signal } from '@angular/core';

import { PortalMeResponse } from './portal-me.model';

/** Portal counterpart of SessionStore — read-only session state, hydrated by
 * PortalSessionService. No permissions signal: unlike internal roles, a Portal principal always
 * has exactly the fixed CLIENT permission set (ADR-PORTAL-AUTH-001), so there is nothing
 * per-account to store — screens are gated by isAuthenticated only. */
@Injectable({ providedIn: 'root' })
export class PortalSessionStore {
  private readonly clientSignal = signal<PortalMeResponse | null>(null);

  readonly client = this.clientSignal.asReadonly();
  readonly isHydrated = computed(() => this.clientSignal() !== null);

  setClient(client: PortalMeResponse): void {
    this.clientSignal.set(client);
  }

  clear(): void {
    this.clientSignal.set(null);
  }
}
