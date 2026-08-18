import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../environments/environment';
import { PortalAuthService } from './portal-auth.service';

describe('PortalAuthService', () => {
  let service: PortalAuthService;
  let httpMock: HttpTestingController;
  let assignSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortalAuthService);
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

  it('login redirects to the brika-portal authorize endpoint with PKCE params, using distinct session keys', async () => {
    await service.login('/portal/cases/c1');

    expect(assignSpy).toHaveBeenCalledTimes(1);
    const url = new URL(assignSpy.mock.calls[0][0] as string);
    expect(url.origin + url.pathname).toBe(
      `${environment.portalOidc.issuer}/protocol/openid-connect/auth`,
    );
    expect(url.searchParams.get('client_id')).toBe(environment.portalOidc.clientId);
    expect(url.searchParams.get('redirect_uri')).toBe(environment.portalOidc.redirectUri);
    expect(url.searchParams.get('code_challenge_method')).toBe('S256');
    expect(url.searchParams.get('state')).toBe(sessionStorage.getItem('brika.portal.pkce.state'));
    expect(sessionStorage.getItem('brika.portal.pkce.return_url')).toBe('/portal/cases/c1');
    // Never collides with the internal auth flow's keys.
    expect(sessionStorage.getItem('brika.pkce.state')).toBeNull();
  });

  it('handleCallback exchanges the code against the brika-portal token endpoint', async () => {
    await service.login('/portal');
    const state = sessionStorage.getItem('brika.portal.pkce.state');

    const resultPromise = service.handleCallback(
      `${environment.portalOidc.redirectUri}?code=auth-code-123&state=${state}`,
    );

    const req = httpMock.expectOne(
      `${environment.portalOidc.issuer}/protocol/openid-connect/token`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toContain('code=auth-code-123');
    req.flush({
      access_token: 'portal-access-123',
      refresh_token: 'portal-refresh-123',
      id_token: 'portal-id-123',
      expires_in: 300,
      token_type: 'Bearer',
    });

    const returnUrl = await resultPromise;
    expect(returnUrl).toBe('/portal');
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('portal-access-123');
  });

  it('handleCallback rejects a mismatched state without calling the token endpoint', async () => {
    await service.login('/portal');

    await expect(
      service.handleCallback(
        `${environment.portalOidc.redirectUri}?code=abc&state=not-the-real-state`,
      ),
    ).rejects.toThrow(/state/i);
    httpMock.expectNone(`${environment.portalOidc.issuer}/protocol/openid-connect/token`);
  });

  it('logout clears the session and redirects to the brika-portal logout endpoint', async () => {
    await service.login('/portal');
    const state = sessionStorage.getItem('brika.portal.pkce.state');
    const resultPromise = service.handleCallback(
      `${environment.portalOidc.redirectUri}?code=abc&state=${state}`,
    );
    httpMock
      .expectOne(`${environment.portalOidc.issuer}/protocol/openid-connect/token`)
      .flush({ access_token: 'a', refresh_token: 'r', id_token: 'i', expires_in: 300, token_type: 'Bearer' });
    await resultPromise;

    assignSpy.mockClear();
    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    const url = new URL(assignSpy.mock.calls[0][0] as string);
    expect(url.origin + url.pathname).toBe(
      `${environment.portalOidc.issuer}/protocol/openid-connect/logout`,
    );
  });

  it('clearSession clears in-memory state without redirecting', async () => {
    await service.login('/portal');
    const state = sessionStorage.getItem('brika.portal.pkce.state');
    const resultPromise = service.handleCallback(
      `${environment.portalOidc.redirectUri}?code=abc&state=${state}`,
    );
    httpMock
      .expectOne(`${environment.portalOidc.issuer}/protocol/openid-connect/token`)
      .flush({ access_token: 'a', refresh_token: 'r', id_token: 'i', expires_in: 300, token_type: 'Bearer' });
    await resultPromise;

    assignSpy.mockClear();
    service.clearSession();

    expect(service.isAuthenticated()).toBe(false);
    expect(assignSpy).not.toHaveBeenCalled();
  });
});
