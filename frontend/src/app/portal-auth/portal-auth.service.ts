import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../environments/environment';
import { SKIP_AUTH } from '../core/http/http-context';
import { generateCodeChallenge, generateRandomString } from '../auth/pkce';
import { TokenResponse, TokenSet } from '../auth/oidc.model';

const CODE_VERIFIER_KEY = 'brika.portal.pkce.code_verifier';
const STATE_KEY = 'brika.portal.pkce.state';
const RETURN_URL_KEY = 'brika.portal.pkce.return_url';

/**
 * Portal Cliente counterpart of AuthService (ADR-PORTAL-AUTH-001, Sprint 19 ADR-PROCESS-007) —
 * Authorization Code + PKCE against the separate `brika-portal` Keycloak realm
 * (environment.portalOidc), never the internal `brika` realm. Deliberately a fully independent
 * service and token store rather than a parametrized AuthService: the two surfaces must never be
 * able to share or confuse a token, mirroring the backend's two independent SecurityFilterChains.
 * Uses distinct sessionStorage keys (brika.portal.*) so a concurrent internal login in another tab
 * can never collide with the Portal PKCE handshake. Same trade-offs as AuthService: tokens live in
 * memory only, no silent (iframe) session recovery on reload.
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

  /** Redirects the browser to Keycloak's authorization endpoint. Never resolves. */
  async login(returnUrl = '/portal'): Promise<void> {
    const codeVerifier = generateRandomString();
    const state = generateRandomString(16);
    const codeChallenge = await generateCodeChallenge(codeVerifier);

    sessionStorage.setItem(CODE_VERIFIER_KEY, codeVerifier);
    sessionStorage.setItem(STATE_KEY, state);
    sessionStorage.setItem(RETURN_URL_KEY, returnUrl);

    const params = new URLSearchParams({
      response_type: 'code',
      client_id: environment.portalOidc.clientId,
      redirect_uri: environment.portalOidc.redirectUri,
      scope: environment.portalOidc.scope,
      state,
      code_challenge: codeChallenge,
      code_challenge_method: 'S256',
    });

    window.location.assign(
      `${environment.portalOidc.issuer}/protocol/openid-connect/auth?${params}`,
    );
  }

  /** Completes the flow after Keycloak redirects back to /portal/auth/callback. Returns the
   * return URL the caller should navigate to. Throws on any mismatch. */
  async handleCallback(callbackUrl: string): Promise<string> {
    const url = new URL(callbackUrl);
    const code = url.searchParams.get('code');
    const state = url.searchParams.get('state');
    const error = url.searchParams.get('error');

    const expectedState = sessionStorage.getItem(STATE_KEY);
    const codeVerifier = sessionStorage.getItem(CODE_VERIFIER_KEY);
    const returnUrl = sessionStorage.getItem(RETURN_URL_KEY) ?? '/portal';
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
      redirect_uri: environment.portalOidc.redirectUri,
      client_id: environment.portalOidc.clientId,
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
      post_logout_redirect_uri: environment.portalOidc.postLogoutRedirectUri,
    });
    if (idToken) {
      params.set('id_token_hint', idToken);
    }
    window.location.assign(
      `${environment.portalOidc.issuer}/protocol/openid-connect/logout?${params}`,
    );
  }

  /** Clears in-memory state only, without redirecting — used when a 401 proves the session is
   * already invalid server-side. */
  clearSession(): void {
    this.clearTokens();
  }

  private async requestToken(body: URLSearchParams): Promise<TokenResponse> {
    return firstValueFrom(
      this.http.post<TokenResponse>(
        `${environment.portalOidc.issuer}/protocol/openid-connect/token`,
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
        client_id: environment.portalOidc.clientId,
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
