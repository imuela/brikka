import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { UserService } from './user.service';
import { User } from './user.model';

describe('UserService', () => {
  let service: UserService;
  let httpMock: HttpTestingController;

  const user: User = {
    id: 'u1',
    companyId: 'co1',
    email: 'broker@brika.test',
    firstName: 'Demo',
    lastName: 'Broker',
    role: 'BROKER',
    status: 'ACTIVE',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(UserService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/users', () => {
    service.list().subscribe((result) => expect(result).toEqual([user]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`);
    expect(req.request.method).toBe('GET');
    req.flush([user]);
  });

  it('get(id) calls GET /api/v1/users/{id}', () => {
    service.get('u1').subscribe((result) => expect(result).toEqual(user));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users/u1`);
    expect(req.request.method).toBe('GET');
    req.flush(user);
  });

  it('create() POSTs the exact CreateUserApiRequest shape', () => {
    const request = {
      email: 'broker@brika.test',
      firstName: 'Demo',
      lastName: 'Broker',
      role: 'BROKER',
      externalIdentityId: 'ext-1',
    };
    service.create(request).subscribe((result) => expect(result).toEqual(user));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(user);
  });

  it('update(id) PATCHes only firstName/lastName', () => {
    const request = { firstName: 'Demo', lastName: 'Broker' };
    service.update('u1', request).subscribe((result) => expect(result).toEqual(user));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users/u1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(user);
  });

  it('disable(id) POSTs to /disable', () => {
    service.disable('u1').subscribe((result) => expect(result).toEqual({ ...user, status: 'DISABLED' }));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users/u1/disable`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...user, status: 'DISABLED' });
  });
});
