import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { SKIP_AUTH } from '../core/http/http-context';
import { generateCodeChallenge, generateRandomString } from './pkce';
import { TokenResponse, TokenSet } from './oidc.model';

const CODE_VERIFIER_KEY = 'brika.pkce.code_verifier';
const STATE_KEY = 'brika.pkce.state';
const RETURN_URL_KEY = 'brika.pkce.return_url';

/**
 * Authorization Code + PKCE against Keycloak (19_IDENTITY_OAUTH_SPECIFICATION.md §4,
 * ADR-FRONTEND-001 D2). Tokens live only in memory — never in localStorage/sessionStorage
 * (§5, "no guardar secretos en localStorage") — so a hard page reload loses the session and
 * requires an interactive re-login; only the one-time, short-lived PKCE `code_verifier`/`state`
 * pair is held in sessionStorage across the redirect to Keycloak, since it is a single-use nonce,
 * not a bearer credential. Silent (iframe-based) session recovery on reload is deliberately out of
 * scope for Sprint 13 (ADR-FRONTEND-001) — a disclosed UX trade-off, not an oversight.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenSet = signal<TokenSet | null>(null);
  private refreshTimer: ReturnType<typeof setTimeout> | null = null;

  readonly isAuthenticated = computed(() => this.tokenSet() !== null);

  private readonly http = inject(HttpClient);

  accessToken(): string | null {
    return this.tokenSet()?.accessToken ?? null;
  }

  /** Redirects the browser to Keycloak's authorization endpoint. Never resolves. */
  async login(returnUrl = '/'): Promise<void> {
    const codeVerifier = generateRandomString();
    const state = generateRandomString(16);
    const codeChallenge = await generateCodeChallenge(codeVerifier);

    sessionStorage.setItem(CODE_VERIFIER_KEY, codeVerifier);
    sessionStorage.setItem(STATE_KEY, state);
    sessionStorage.setItem(RETURN_URL_KEY, returnUrl);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: environment.oidc.clientId,
      redirect_uri: environment.oidc.redirectUri,
      scope: environment.oidc.scope,
      state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
    });

    window.location.assign(`${environment.oidc.issuer}/protocol/openid-connect/auth?${params}`);
  }

  /**
   * Completes the flow after Keycloak redirects back to /auth/callback. Returns the return URL
   * the caller should navigate to. Throws on any mismatch (missing code, state mismatch, token
   * exchange failure) — the callback component is responsible for surfacing that as a login error.
   */
  async handleCallback(callbackUrl: string): Promise<string> {
    const url = new URL(callbackUrl);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    const error = url.searchParams.get('error');

    const expectedState = sessionStorage.getItem(STATE_KEY);
    const codeVerifier = sessionStorage.getItem(CODE_VERIFIER_KEY);
    const returnUrl = sessionStorage.getItem(RETURN_URL_KEY) ?? '/';
    sessionStorage.removeItem(STATE_KEY);
    sessionStorage.removeItem(CODE_VERIFIER_KEY);
    sessionStorage.removeItem(RETURN_URL_KEY);

    if (error) {
      throw new Error(`Keycloak returned an error: ${error}`);
    }
    if (!code || !state || !codeVerifier || state !== expectedState) {
      throw new Error('Invalid OIDC callback: missing or mismatched state/code.');
    }

    const body = new URLSearchParams({
      grant_type: 'authorization_code',
      code,
      redirect_uri: environment.oidc.redirectUri,
      client_id: environment.oidc.clientId,
      code_verifier: codeVerifier,
    });

    const response = await this.requestToken(body);
    this.applyTokenResponse(response);
    return returnUrl;
  }

  logout(): void {
    const idToken = this.tokenSet()?.idToken;
    this.clearTokens();

    const params = new URLSearchParams({
      post_logout_redirect_uri: environment.oidc.postLogoutRedirectUri,
    });
    if (idToken) {
      params.set('id_token_hint', idToken);
    }
    window.location.assign(
      `${environment.oidc.issuer}/protocol/openid-connect/logout?${params}`,
    );
  }

  /** Clears in-memory state only, without redirecting — used when a 401 proves the session is
   * already invalid server-side, so no logout round-trip to Keycloak is needed. */
  clearSession(): void {
    this.clearTokens();
  }

  private async requestToken(body: URLSearchParams): Promise<TokenResponse> {
    return firstValueFrom(
      this.http.post<TokenResponse>(
        `${environment.oidc.issuer}/protocol/openid-connect/token`,
        body.toString(),
        {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          context: new HttpContext().set(SKIP_AUTH, true),
        },
      ),
    );
  }

  private applyTokenResponse(response: TokenResponse): void {
    const expiresAt = Date.now() + response.expires_in * 1000;
    this.tokenSet.set({
      accessToken: response.access_token,
      refreshToken: response.refresh_token ?? null,
      idToken: response.id_token ?? null,
      expiresAt,
    });
    this.scheduleRefresh(response.expires_in);
  }

  private scheduleRefresh(expiresInSeconds: number): void {
    if (this.refreshTimer) {
      clearTimeout(this.refreshTimer);
    }
    // Refresh 30s before expiry, never sooner than 5s from now.
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
      const body = new URLSearchParams({
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
        client_id: environment.oidc.clientId,
      });
      const response = await this.requestToken(body);
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
