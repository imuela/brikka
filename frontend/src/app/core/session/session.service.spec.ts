import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { SessionService } from './session.service';
import { SessionStore } from './session.store';

describe('SessionService', () => {
  let service: SessionService;
  let store: SessionStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionService);
    store = TestBed.inject(SessionStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('hydrate populates the session store from /me and /me/permissions', async () => {
    const hydratePromise = service.hydrate();

    const meReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/me`);
    meReq.flush({
      id: 'u1',
      email: 'manager@brika.test',
      role: 'MANAGER',
      companyId: 'c1',
      entitlements: {},
    });
    const permissionsReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/me/permissions`);
    permissionsReq.flush({ permissions: ['CASE_READ'] });

    await hydratePromise;

    expect(store.user()?.email).toBe('manager@brika.test');
    expect(store.hasPermission('CASE_READ')).toBe(true);
  });

  it('clear delegates to the session store', () => {
    store.setUser({
      id: 'u1',
      email: 'a@b.test',
      role: 'MANAGER',
      companyId: null,
      entitlements: {},
    });

    service.clear();

    expect(store.isHydrated()).toBe(false);
  });
});
