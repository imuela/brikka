import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../environments/environment';
import { PortalSessionService } from './portal-session.service';
import { PortalSessionStore } from './portal-session.store';

describe('PortalSessionService', () => {
  let service: PortalSessionService;
  let store: PortalSessionStore;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortalSessionService);
    store = TestBed.inject(PortalSessionStore);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('hydrate populates the portal session store from /api/v1/portal/me', async () => {
    const hydratePromise = service.hydrate();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/me`);
    req.flush({
      clientId: 'cl1',
      firstName: 'Ada',
      lastName: 'Client',
      email: 'ada@client.test',
      phone: '600000000',
      accountStatus: 'ACTIVE',
      lastLoginAt: null,
    });

    await hydratePromise;

    expect(store.client()?.firstName).toBe('Ada');
    expect(store.isHydrated()).toBe(true);
  });

  it('clear delegates to the portal session store', () => {
    store.setClient({
      clientId: 'cl1',
      firstName: 'Ada',
      lastName: 'Client',
      email: 'ada@client.test',
      phone: null,
      accountStatus: 'ACTIVE',
      lastLoginAt: null,
    });

    service.clear();

    expect(store.isHydrated()).toBe(false);
  });
});
