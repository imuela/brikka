import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../environments/environment';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  const LOGIN_URL = `${environment.apiBaseUrl}/api/v1/auth/login`;
  const REFRESH_URL = `${environment.apiBaseUrl}/api/v1/auth/refresh`;
  const LOGOUT_URL = `${environment.apiBaseUrl}/api/v1/auth/logout`;
  const RESET_REQUEST_URL = `${environment.apiBaseUrl}/api/v1/auth/password-reset/request`;
  const RESET_CONFIRM_URL = `${environment.apiBaseUrl}/api/v1/auth/password-reset/confirm`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
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

  it('login posts credentials and stores the returned tokens', async () => {
    const resultPromise = service.login('manager@brika.test', 'correct-horse');

    const req = httpMock.expectOne(LOGIN_URL);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ email: 'manager@brika.test', password: 'correct-horse' });
    req.flush({ accessToken: 'access-123', refreshToken: 'refresh-123', expiresInSeconds: 900 });

    await resultPromise;
    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('access-123');
  });

  it('login rejects with a parsed ApiError on invalid credentials', async () => {
    const resultPromise = service.login('manager@brika.test', 'wrong');

    httpMock
      .expectOne(LOGIN_URL)
      .flush(
        { code: 'UNAUTHENTICATED', message: 'Invalid credentials', requestId: 'r-1' },
        { status: 401, statusText: 'Unauthorized' },
      );

    await expect(resultPromise).rejects.toMatchObject({ status: 401, code: 'UNAUTHENTICATED' });
    expect(service.isAuthenticated()).toBe(false);
  });

  it('logout clears the session immediately and best-effort revokes the refresh token', async () => {
    const loginPromise = service.login('manager@brika.test', 'correct-horse');
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
    const loginPromise = service.login('manager@brika.test', 'correct-horse');
    httpMock
      .expectOne(LOGIN_URL)
      .flush({ accessToken: 'a', refreshToken: 'r', expiresInSeconds: 900 });
    await loginPromise;

    service.clearSession();

    expect(service.isAuthenticated()).toBe(false);
    httpMock.expectNone(LOGOUT_URL);
  });

  it('requestPasswordReset posts the email and resolves regardless of match (backend contract)', async () => {
    const resultPromise = service.requestPasswordReset('someone@brika.test');

    const req = httpMock.expectOne(RESET_REQUEST_URL);
    expect(req.request.body).toEqual({ email: 'someone@brika.test' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(resultPromise).resolves.toBeUndefined();
  });

  it('confirmPasswordReset posts the token and new password', async () => {
    const resultPromise = service.confirmPasswordReset('reset-token', 'New-Password-1');

    const req = httpMock.expectOne(RESET_CONFIRM_URL);
    expect(req.request.body).toEqual({ token: 'reset-token', newPassword: 'New-Password-1' });
    req.flush(null, { status: 204, statusText: 'No Content' });

    await expect(resultPromise).resolves.toBeUndefined();
  });

  it('refresh replaces the token pair when the scheduled refresh fires', async () => {
    vi.useFakeTimers();
    try {
      const loginPromise = service.login('manager@brika.test', 'correct-horse');
      httpMock
        .expectOne(LOGIN_URL)
        .flush({ accessToken: 'a1', refreshToken: 'r1', expiresInSeconds: 35 });
      await loginPromise;

      // scheduleRefresh() fires 30s before expiry, clamped to a 5s minimum — here (35 - 30) * 1000
      // clamps to 5000ms.
      await vi.advanceTimersByTimeAsync(5000);

      const req = httpMock.expectOne(REFRESH_URL);
      expect(req.request.body).toEqual({ refreshToken: 'r1' });
      req.flush({ accessToken: 'a2', refreshToken: 'r2', expiresInSeconds: 900 });
      await vi.advanceTimersByTimeAsync(0);

      expect(service.accessToken()).toBe('a2');
    } finally {
      vi.useRealTimers();
    }
  });

  it('login persists only the refresh token to sessionStorage', async () => {
    const loginPromise = service.login('manager@brika.test', 'correct-horse');
    httpMock
      .expectOne(LOGIN_URL)
      .flush({ accessToken: 'access-123', refreshToken: 'refresh-123', expiresInSeconds: 900 });
    await loginPromise;

    expect(sessionStorage.getItem(AuthService.refreshTokenStorageKey)).toBe('refresh-123');
  });

  it('restore returns false and makes no network call when no refresh token is stored', async () => {
    await expect(service.restore()).resolves.toBe(false);
    httpMock.expectNone(REFRESH_URL);
  });

  it('restore exchanges the stored refresh token and becomes authenticated', async () => {
    sessionStorage.setItem(AuthService.refreshTokenStorageKey, 'stored-refresh');

    const restorePromise = service.restore();

    const req = httpMock.expectOne(REFRESH_URL);
    expect(req.request.body).toEqual({ refreshToken: 'stored-refresh' });
    req.flush({ accessToken: 'a2', refreshToken: 'r2', expiresInSeconds: 900 });
    await restorePromise;

    expect(service.isAuthenticated()).toBe(true);
    expect(service.accessToken()).toBe('a2');
    expect(sessionStorage.getItem(AuthService.refreshTokenStorageKey)).toBe('r2');
  });

  it('restore clears the stored token and stays logged out when the refresh fails', async () => {
    sessionStorage.setItem(AuthService.refreshTokenStorageKey, 'expired-refresh');

    const restorePromise = service.restore();

    httpMock
      .expectOne(REFRESH_URL)
      .flush(
        { code: 'INVALID_REFRESH_TOKEN', message: 'bad', requestId: 'r-1' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await restorePromise;

    expect(service.isAuthenticated()).toBe(false);
    expect(sessionStorage.getItem(AuthService.refreshTokenStorageKey)).toBeNull();
  });

  it('logout removes the persisted refresh token', async () => {
    const loginPromise = service.login('manager@brika.test', 'correct-horse');
    httpMock
      .expectOne(LOGIN_URL)
      .flush({ accessToken: 'a', refreshToken: 'r', expiresInSeconds: 900 });
    await loginPromise;

    service.logout();
    httpMock.expectOne(LOGOUT_URL).flush(null, { status: 204, statusText: 'No Content' });

    expect(sessionStorage.getItem(AuthService.refreshTokenStorageKey)).toBeNull();
  });
});
