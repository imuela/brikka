import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let assignSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);

    assignSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { ...window.location, assign: assignSpy },
      writable: true,
    });
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('is not authenticated initially', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken()).toBeNull();
  });

  it('login redirects to the Keycloak authorize endpoint with PKCE params and stores state', async () => {
    await service.login('/app/dashboard');

    expect(assignSpy).toHaveBeenCalledTimes(1);
    const url = new URL(assignSpy.mock.calls[0][0] as string);
    expect(url.origin + url.pathname).toBe(
      `${environment.oidc.issuer}/protocol/openid-connect/auth`,
    );
    expect(url.searchParams.get('response_type')).toBe('code');
    expect(url.searchParams.get('client_id')).toBe(environment.oidc.clientId);
    expect(url.searchParams.get('redirect_uri')).toBe(environment.oidc.redirectUri);
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    expect(url.searchParams.get('code_challenge')).toBeTruthy();
    expect(url.searchParams.get('state')).toBe(sessionStorage.getItem('brika.pkce.state'));
    expect(sessionStorage.getItem('brika.pkce.return_url')).toBe('/app/dashboard');
  });

  it('handleCallback exchanges the code for tokens and resolves the stored return URL', async () => {
    await service.login('/app/dashboard');
    const state = sessionStorage.getItem('brika.pkce.state');

    const resultPromise = service.handleCallback(
      `${environment.oidc.redirectUri}?code=auth-code-123&state=${state}`,
    );

    const req = httpMock.expectOne(`${environment.oidc.issuer}/protocol/openid-connect/token`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toContain('grant_type=authorization_code');
    expect(req.request.body).toContain('code=auth-code-123');
    req.flush({
      access_token: 'access-123',
      refresh_token: 'refresh-123',
      id_token: 'id-123',
      expires_in: 300,
      token_type: 'Bearer',
    });

    const returnUrl = await resultPromise;
    expect(returnUrl).toBe('/app/dashboard');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('access-123');
    // The one-time PKCE nonce must never survive past the exchange it was bound to.
    expect(sessionStorage.getItem('brika.pkce.code_verifier')).toBeNull();
    expect(sessionStorage.getItem('brika.pkce.state')).toBeNull();
  });

  it('handleCallback rejects a mismatched state without calling the token endpoint', async () => {
    await service.login('/app');

    await expect(
      service.handleCallback(`${environment.oidc.redirectUri}?code=abc&state=not-the-real-state`),
    ).rejects.toThrow(/state/i);
    httpMock.expectNone(`${environment.oidc.issuer}/protocol/openid-connect/token`);
  });

  it('handleCallback surfaces an error param returned by Keycloak', async () => {
    await expect(
      service.handleCallback(`${environment.oidc.redirectUri}?error=access_denied`),
    ).rejects.toThrow(/access_denied/);
  });

  it('logout clears the session and redirects to the Keycloak logout endpoint', async () => {
    await service.login('/app');
    const state = sessionStorage.getItem('brika.pkce.state');
    const resultPromise = service.handleCallback(
      `${environment.oidc.redirectUri}?code=abc&state=${state}`,
    );
    httpMock
      .expectOne(`${environment.oidc.issuer}/protocol/openid-connect/token`)
      .flush({ access_token: 'a', refresh_token: 'r', id_token: 'i', expires_in: 300, token_type: 'Bearer' });
    await resultPromise;

    assignSpy.mockClear();
    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    expect(assignSpy).toHaveBeenCalledTimes(1);
    const url = new URL(assignSpy.mock.calls[0][0] as string);
    expect(url.origin + url.pathname).toBe(
      `${environment.oidc.issuer}/protocol/openid-connect/logout`,
    );
    expect(url.searchParams.get('id_token_hint')).toBe('i');
  });

  it('clearSession clears in-memory state without redirecting', async () => {
    await service.login('/app');
    const state = sessionStorage.getItem('brika.pkce.state');
    const resultPromise = service.handleCallback(
      `${environment.oidc.redirectUri}?code=abc&state=${state}`,
    );
    httpMock
      .expectOne(`${environment.oidc.issuer}/protocol/openid-connect/token`)
      .flush({ access_token: 'a', refresh_token: 'r', id_token: 'i', expires_in: 300, token_type: 'Bearer' });
    await resultPromise;

    assignSpy.mockClear();
    service.clearSession();

    expect(service.isAuthenticated()).toBe(false);
    expect(assignSpy).not.toHaveBeenCalled();
  });
});
