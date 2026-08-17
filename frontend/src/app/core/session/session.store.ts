import { Injectable, computed, signal } from '@angular/core';

import { MeResponse } from './me.model';

/** Read-only session state, hydrated by SessionService. Nothing here calls the API directly —
 * keeps the store a pure signal holder, testable without HTTP mocks. */
@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly userSignal = signal<MeResponse | null>(null);
  private readonly permissionsSignal = signal<ReadonlySet<string>>(new Set());

  readonly user = this.userSignal.asReadonly();
  readonly isHydrated = computed(() => this.userSignal() !== null);
  readonly role = computed(() => this.userSignal()?.role ?? null);
  readonly companyId = computed(() => this.userSignal()?.companyId ?? null);

  setUser(user: MeResponse): void {
    this.userSignal.set(user);
  }

  setPermissions(permissions: readonly string[]): void {
    this.permissionsSignal.set(new Set(permissions));
  }

  hasPermission(code: string): boolean {
    return this.permissionsSignal().has(code);
  }

  clear(): void {
    this.userSignal.set(null);
    this.permissionsSignal.set(new Set());
  }
}
