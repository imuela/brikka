import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { TokenSet, AccessTokenApiResponse } from '../auth/token-set.model';
import { ApiError, toApiError } from '../core/http/api-error';
import { SKIP_AUTH } from '../core/http/http-context';

const PORTAL_AUTH_BASE = `${environment.apiBaseUrl}/api/v1/portal/auth`;

/**
 * Portal Cliente counterpart of AuthService — deliberately a full duplicate, never sharing an
 * implementation (ADR-PORTAL-AUTH-001, Sprint 22 authorization §4): the two surfaces must never
 * be able to share or confuse a token, mirroring the backend's two independent
 * SecurityFilterChains and independent signing keys.
 */
@Injectable({ providedIn: 'root' })
export class PortalAuthService {
  private readonly tokenSet = signal<TokenSet | null>(null);
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isAuthenticated = computed(() => this.tokenSet() !== null);

  private readonly http = inject(HttpClient);

  accessToken(): string | null {
    return this.tokenSet()?.accessToken ?? null;
  }

  async login(email: string, password: string): Promise<void> {
    const response = await this.post<AccessTokenApiResponse>(`${PORTAL_AUTH_BASE}/login`, {
      email,
      password,
    });
    this.applyTokenResponse(response);
  }

  logout(): void {
    const refreshToken = this.tokenSet()?.refreshToken;
    this.clearTokens();
    if (refreshToken) {
      void firstValueFrom(
        this.http.post(
          `${PORTAL_AUTH_BASE}/logout`,
          { refreshToken },
          { context: new HttpContext().set(SKIP_AUTH, true) },
        ),
      ).catch(() => undefined);
    }
  }

  /** Clears in-memory state only, without a network round-trip. */
  clearSession(): void {
    this.clearTokens();
  }

  async requestPasswordReset(email: string): Promise<void> {
    await this.post<void>(`${PORTAL_AUTH_BASE}/password-reset/request`, { email });
  }

  async confirmPasswordReset(token: string, newPassword: string): Promise<void> {
    await this.post<void>(`${PORTAL_AUTH_BASE}/password-reset/confirm`, { token, newPassword });
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

  private applyTokenResponse(response: AccessTokenApiResponse): void {
    const expiresAt = Date.now() + response.expiresInSeconds * 1000;
    this.tokenSet.set({
      accessToken: response.accessToken,
      refreshToken: response.refreshToken,
      expiresAt,
    });
    this.scheduleRefresh(response.expiresInSeconds);
  }

  private scheduleRefresh(expiresInSeconds: number): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }
    const delayMs = Math.max((expiresInSeconds - 30) * 1000, 5000);
    this.refreshTimer = setTimeout(() => void this.refresh(), delayMs);
  }

  private async refresh(): Promise<void> {
    const refreshToken = this.tokenSet()?.refreshToken;
    if (!refreshToken) {
      this.clearTokens();
      return;
    }
    try {
      const response = await firstValueFrom(
        this.http.post<AccessTokenApiResponse>(`${PORTAL_AUTH_BASE}/refresh`, { refreshToken }),
      );
      this.applyTokenResponse(response);
    } catch {
      this.clearTokens();
    }
  }

  private clearTokens(): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
      this.refreshTimer = null;
    }
    this.tokenSet.set(null);
  }
}
