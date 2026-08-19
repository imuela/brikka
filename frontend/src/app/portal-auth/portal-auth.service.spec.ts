import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../environments/environment';
import { PortalAuthService } from './portal-auth.service';

describe('PortalAuthService', () => {
  let service: PortalAuthService;
  let httpMock: HttpTestingController;

  const LOGIN_URL = `${environment.apiBaseUrl}/api/v1/portal/auth/login`;
  const LOGOUT_URL = `${environment.apiBaseUrl}/api/v1/portal/auth/logout`;
  const RESET_REQUEST_URL = `${environment.apiBaseUrl}/api/v1/portal/auth/password-reset/request`;
  const RESET_CONFIRM_URL = `${environment.apiBaseUrl}/api/v1/portal/auth/password-reset/confirm`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortalAuthService);
    httpMock = TestBed.inject(HttpTestingController);
    sessionStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('is not authenticated initially', () => {
    expect(service.isAuthenticated()).toBe(false);
    expect(service.accessToken()).toBeNull();
  });

  it('login posts credentials to the Portal endpoint and stores the returned tokens', async () => {
    const resultPromise = service.login('client@brika.test', 'correct-horse');

    const req = httpMock.expectOne(LOGIN_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'client@brika.test', password: 'correct-horse' });
    req.flush({ accessToken: 'access-p1', refreshToken: 'refresh-p1', expiresInSeconds: 900 });

    await resultPromise;
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('access-p1');
  });

  it('login rejects with a parsed ApiError on invalid credentials', async () => {
    const resultPromise = service.login('client@brika.test', 'wrong');

    httpMock
      .expectOne(LOGIN_URL)
      .flush(
        { code: 'UNAUTHENTICATED', message: 'Invalid credentials', requestId: 'r-1' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(resultPromise).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED' });
    expect(service.isAuthenticated()).toBe(false);
  });

  it('logout clears the session and best-effort revokes the refresh token', async () => {
    const loginPromise = service.login('client@brika.test', 'correct-horse');
    httpMock
      .expectOne(LOGIN_URL)
      .flush({ accessToken: 'a', refreshToken: 'r', expiresInSeconds: 900 });
    await loginPromise;

    service.logout();

    expect(service.isAuthenticated()).toBe(false);
    const req = httpMock.expectOne(LOGOUT_URL);
    expect(req.request.body).toEqual({ refreshToken: 'r' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('clearSession clears in-memory state without any network call', async () => {
    const loginPromise = service.login('client@brika.test', 'correct-horse');
    httpMock
      .expectOne(LOGIN_URL)
      .flush({ accessToken: 'a', refreshToken: 'r', expiresInSeconds: 900 });
    await loginPromise;

    service.clearSession();

    expect(service.isAuthenticated()).toBe(false);
    httpMock.expectNone(LOGOUT_URL);
  });

  it('requestPasswordReset posts the email to the Portal endpoint', async () => {
    const resultPromise = service.requestPasswordReset('client@brika.test');

    const req = httpMock.expectOne(RESET_REQUEST_URL);
    expect(req.request.body).toEqual({ email: 'client@brika.test' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(resultPromise).resolves.toBeUndefined();
  });

  it('confirmPasswordReset posts the token and new password to the Portal endpoint', async () => {
    const resultPromise = service.confirmPasswordReset('reset-token', 'New-Password-1');

    const req = httpMock.expectOne(RESET_CONFIRM_URL);
    expect(req.request.body).toEqual({ token: 'reset-token', newPassword: 'New-Password-1' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(resultPromise).resolves.toBeUndefined();
  });

  it('login persists only the Portal refresh token under its own storage key', async () => {
    const loginPromise = service.login('client@brika.test', 'correct-horse');
    httpMock
      .expectOne(LOGIN_URL)
      .flush({ accessToken: 'access-p1', refreshToken: 'refresh-p1', expiresInSeconds: 900 });
    await loginPromise;

    expect(sessionStorage.getItem(PortalAuthService.refreshTokenStorageKey)).toBe('refresh-p1');
    expect(sessionStorage.getItem('brika.session.refreshToken')).toBeNull();
  });

  it('restore returns false and makes no network call when no refresh token is stored', async () => {
    await expect(service.restore()).resolves.toBe(false);
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/portal/auth/refresh`);
  });

  it('restore exchanges the stored Portal refresh token and becomes authenticated', async () => {
    sessionStorage.setItem(PortalAuthService.refreshTokenStorageKey, 'stored-portal-refresh');

    const restorePromise = service.restore();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/auth/refresh`);
    expect(req.request.body).toEqual({ refreshToken: 'stored-portal-refresh' });
    req.flush({ accessToken: 'pa2', refreshToken: 'pr2', expiresInSeconds: 900 });
    await restorePromise;

    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('pa2');
  });

  it('restore clears the stored token and stays logged out when the refresh fails', async () => {
    sessionStorage.setItem(PortalAuthService.refreshTokenStorageKey, 'expired-portal-refresh');

    const restorePromise = service.restore();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/auth/refresh`)
      .flush(
        { code: 'INVALID_REFRESH_TOKEN', message: 'bad', requestId: 'r-1' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await restorePromise;

    expect(service.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(PortalAuthService.refreshTokenStorageKey)).toBeNull();
  });

  it('logout removes the persisted Portal refresh token', async () => {
    const loginPromise = service.login('client@brika.test', 'correct-horse');
    httpMock
      .expectOne(LOGIN_URL)
      .flush({ accessToken: 'a', refreshToken: 'r', expiresInSeconds: 900 });
    await loginPromise;

    service.logout();
    httpMock.expectOne(LOGOUT_URL).flush(null, { status: 204, statusText: 'No Content' });

    expect(sessionStorage.getItem(PortalAuthService.refreshTokenStorageKey)).toBeNull();
  });
});
