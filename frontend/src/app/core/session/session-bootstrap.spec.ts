import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { AuthService } from '../../auth/auth.service';
import { environment } from '../../../environments/environment';
import { PortalAuthService } from '../../portal-auth/portal-auth.service';
import { PortalSessionService } from '../../portal-auth/portal-session.service';
import { PortalSessionStore } from '../../portal-auth/portal-session.store';
import { SessionService } from './session.service';
import { SessionStore } from './session.store';
import { restoreAndHydrate } from './session-bootstrap';

describe('restoreAndHydrate', () => {
  let httpMock: HttpTestingController;

  let authService: AuthService;
  let sessionService: SessionService;
  let sessionStore: SessionStore;
  let portalAuthService: PortalAuthService;
  let portalSessionService: PortalSessionService;
  let portalSessionStore: PortalSessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    sessionService = TestBed.inject(SessionService);
    sessionStore = TestBed.inject(SessionStore);
    portalAuthService = TestBed.inject(PortalAuthService);
    portalSessionService = TestBed.inject(PortalSessionService);
    portalSessionStore = TestBed.inject(PortalSessionStore);
    sessionStorage.clear();
  });

  afterEach(() => httpMock.verify());

  /** Lets pending microtasks settle so a request dispatched after an await is observable. */
  const tick = () => new Promise<void>((resolve) => setTimeout(resolve));

  it('does nothing and makes no calls when neither surface has a stored token', async () => {
    await restoreAndHydrate(authService, sessionService, portalAuthService, portalSessionService);

    expect(sessionStore.isHydrated()).toBe(false);
    expect(portalSessionStore.isHydrated()).toBe(false);
    expect(authService.isAuthenticated()).toBe(false);
    expect(portalAuthService.isAuthenticated()).toBe(false);
  });

  it('restores and hydrates the internal session when a refresh token is stored', async () => {
    sessionStorage.setItem(AuthService.refreshTokenStorageKey, 'stored-refresh');

    const runPromise = restoreAndHydrate(authService, sessionService, portalAuthService, portalSessionService);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/auth/refresh`)
      .flush({ accessToken: 'a2', refreshToken: 'r2', expiresInSeconds: 900 });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me`)
      .flush({ id: 'u1', email: 'manager@brika.test', role: 'MANAGER', companyId: 'c1', entitlements: {} });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me/permissions`)
      .flush({ permissions: ['CASE_READ'] });
    await runPromise;

    expect(authService.isAuthenticated()).toBe(true);
    expect(sessionStore.user()?.email).toBe('manager@brika.test');
    expect(sessionStore.hasPermission('CASE_READ')).toBe(true);
  });

  it('tears down the internal session when hydration fails, never leaving a partial state', async () => {
    sessionStorage.setItem(AuthService.refreshTokenStorageKey, 'stored-refresh');

    const runPromise = restoreAndHydrate(authService, sessionService, portalAuthService, portalSessionService);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/auth/refresh`)
      .flush({ accessToken: 'a2', refreshToken: 'r2', expiresInSeconds: 900 });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me`)
      .flush({ code: 'SERVER_ERROR', message: 'boom', requestId: 'r-1' }, { status: 500, statusText: 'Server Error' });
    await tick();
    // hydrate() races /me and /me/permissions with Promise.all: /me failed first, so the
    // permissions request stays pending — flush it so the mock verifies clean.
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me/permissions`)
      .flush({ permissions: [] });
    await runPromise;

    expect(authService.isAuthenticated()).toBe(false);
    expect(sessionStore.isHydrated()).toBe(false);
    expect(sessionStorage.getItem(AuthService.refreshTokenStorageKey)).toBeNull();
  });

  it('restores and hydrates the Portal session independently', async () => {
    sessionStorage.setItem(PortalAuthService.refreshTokenStorageKey, 'stored-portal-refresh');

    const runPromise = restoreAndHydrate(authService, sessionService, portalAuthService, portalSessionService);

    // The internal restore (no stored token) settles first, then the Portal restore dispatches —
    // let those microtasks flush before expecting the Portal refresh request.
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/auth/refresh`)
      .flush({ accessToken: 'pa2', refreshToken: 'pr2', expiresInSeconds: 900 });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/me`)
      .flush({ clientId: 'cl1', firstName: 'Ada', lastName: 'Client', email: 'ada@client.test', phone: null, accountStatus: 'ACTIVE', lastLoginAt: null });
    await runPromise;

    expect(portalAuthService.isAuthenticated()).toBe(true);
    expect(portalSessionStore.client()?.firstName).toBe('Ada');
    expect(portalSessionStore.isHydrated()).toBe(true);
    expect(authService.isAuthenticated()).toBe(false);
  });
});