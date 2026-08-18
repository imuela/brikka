import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { PortalProfileService } from './portal-profile.service';
import { PortalMeResponse } from '../../portal-auth/portal-me.model';

describe('PortalProfileService', () => {
  let service: PortalProfileService;
  let httpMock: HttpTestingController;

  const me: PortalMeResponse = {
    clientId: 'cl1',
    firstName: 'Ada',
    lastName: 'Client',
    email: 'new@client.test',
    phone: '611111111',
    accountStatus: 'ACTIVE',
    lastLoginAt: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortalProfileService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('update() PATCHes the exact UpdatePortalProfileApiRequest shape', () => {
    const request = { email: 'new@client.test', phone: '611111111' };
    service.update(request).subscribe((result) => expect(result).toEqual(me));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/profile`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(me);
  });
});
