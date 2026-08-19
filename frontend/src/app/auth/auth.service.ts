import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { ApiError, toApiError } from '../core/http/api-error';
import { SKIP_AUTH } from '../core/http/http-context';
import { AccessTokenApiResponse, TokenSet } from './token-set.model';

const AUTH_BASE = `${environment.apiBaseUrl}/api/v1/auth`;

/**
 * Sprint 22 authorization (27_KEYCLOAK_REMOVAL_ANALYSIS.md, Opción A): email+password login
 * against Brika's own token issuer, replacing the Keycloak Authorization Code + PKCE redirect
 * flow. login/refresh/logout are marked SKIP_AUTH: there is no bearer token yet to attach, and a
 * 401 from these endpoints must never trigger errorInterceptor's "session expired, redirect to
 * /login" handling — it means "wrong credentials" or "expired reset token", not "your live session
 * died", so callers get a normal ApiError to show inline instead.
 *
 * Sprint 23 (session hydration): the access token still lives only in memory (signal), but the
 * refresh token is now also persisted in sessionStorage so a page reload in the same tab can
 * recover the session without a fresh login (ADR-FRONTEND-001, amended Sprint 23). sessionStorage,
 * not localStorage: it survives reload but is dropped when the tab/window closes. A cookie-free,
 * bearer-token XSS protection model keeps the opaque refresh token rotating and revocable
 * server-side, so the stored value is only useful until it is rotated or revoked.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  /** Key under which only the refresh token is kept in sessionStorage for reload recovery. */
  static readonly refreshTokenStorageKey = 'brika.session.refreshToken';

  private readonly tokenSet = signal<TokenSet | null>(null);
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isAuthenticated = computed(() => this.tokenSet() !== null);

  private readonly http = inject(HttpClient);

  accessToken(): string | null {
    return this.tokenSet()?.accessToken ?? null;
  }

  async login(email: string, password: string): Promise<void> {
    const response = await this.post<AccessTokenApiResponse>(`${AUTH_BASE}/login`, {
      email,
      password,
    });
    this.applyTokenResponse(response);
  }

  logout(): void {
    const refreshToken = this.tokenSet()?.refreshToken;
    this.clearTokens();
    if (refreshToken) {
      // Best-effort: revoke server-side, but a logout must always succeed from the user's point
      // of view even if this request fails (network, already-expired token, etc.).
      void firstValueFrom(
        this.http.post(
          `${AUTH_BASE}/logout`,
          { refreshToken },
          { context: new HttpContext().set(SKIP_AUTH, true) },
        ),
      ).catch(() => undefined);
    }
  }

  /** Clears in-memory state only, without a network round-trip — used when a 401 proves the
   * session is already invalid server-side. */
  clearSession(): void {
    this.clearTokens();
  }

  async requestPasswordReset(email: string): Promise<void> {
    await this.post<void>(`${AUTH_BASE}/password-reset/request`, { email });
  }

  async confirmPasswordReset(token: string, newPassword: string): Promise<void> {
    await this.post<void>(`${AUTH_BASE}/password-reset/confirm`, { token, newPassword });
  }

  private async post<T>(url: string, body: unknown): Promise<T> {
    try {
      return await firstValueFrom(
        this.http.post<T>(url, body, { context: new HttpContext().set(SKIP_AUTH, true) }),
      );
    } catch (error) {
      if (error instanceof HttpErrorResponse) {
        throw toApiError(error) satisfies ApiError;
      }
      throw error;
    }
  }

  /** Restores a session from the persisted refresh token on startup (reload in the same tab).
   * Returns true when a live session was recovered (new token pair in memory), false when there is
   * no stored token or the refresh failed (e.g. expired/revoked) — in which case any stored token
   * is cleared and the app starts logged out. */
  async restore(): Promise<boolean> {
    const storedRefreshToken = sessionStorage.getItem(AuthService.refreshTokenStorageKey);
    if (!storedRefreshToken) {
      return false;
    }
    return this.performRefresh(storedRefreshToken);
  }

  private applyTokenResponse(response: AccessTokenApiResponse): void {
    const expiresAt = Date.now() + response.expiresInSeconds * 1000;
    this.tokenSet.set({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      expiresAt,
    });
    sessionStorage.setItem(AuthService.refreshTokenStorageKey, response.refreshToken);
    this.scheduleRefresh(response.expiresInSeconds);
  }

  private scheduleRefresh(expiresInSeconds: number): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }
    // Refresh 30s before expiry, never sooner than 5s from now.
    const delayMs = Math.max((expiresInSeconds - 30) * 1000, 5000);
    this.refreshTimer = setTimeout(() => void this.refresh(), delayMs);
  }

  /** Performs a single refresh round-trip against the issuer. Returns true on success (tokens
   * applied + rescheduled), false on any failure (state cleared). Shared by the scheduled
   * refresh and the startup restore. */
  private async performRefresh(refreshToken: string): Promise<boolean> {
    try {
      const response = await firstValueFrom(
        this.http.post<AccessTokenApiResponse>(`${AUTH_BASE}/refresh`, { refreshToken }),
      );
      this.applyTokenResponse(response);
      return true;
    } catch {
      this.clearTokens();
      return false;
    }
  }

  private async refresh(): Promise<void> {
    const refreshToken = this.tokenSet()?.refreshToken;
    if (!refreshToken) {
      this.clearTokens();
      return;
    }
    await this.performRefresh(refreshToken);
  }

  private clearTokens(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    sessionStorage.removeItem(AuthService.refreshTokenStorageKey);
    this.tokenSet.set(null);
  }
}
