import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { ClientsService } from './clients.service';
import { Client } from './client.model';

describe('ClientsService', () => {
  let service: ClientsService;
  let httpMock: HttpTestingController;

  const client: Client = {
    id: 'c1',
    companyId: 'co1',
    firstName: 'Ada',
    lastName: 'Lovelace',
    email: 'ada@brika.test',
    phone: '600000000',
    documentType: null,
    documentNumber: null,
    dateOfBirth: null,
    nationality: null,
    address: null,
    employmentStatus: null,
    status: 'ACTIVE',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ClientsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/clients', () => {
    service.list().subscribe((clients) => expect(clients).toEqual([client]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`);
    expect(req.request.method).toBe('GET');
    req.flush([client]);
  });

  it('get(id) calls GET /api/v1/clients/{id}', () => {
    service.get('c1').subscribe((result) => expect(result).toEqual(client));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`);
    expect(req.request.method).toBe('GET');
    req.flush(client);
  });

  it('create() posts the exact CreateClientApiRequest shape', () => {
    const request = { firstName: 'Ada', lastName: 'Lovelace', email: 'ada@brika.test', phone: '600000000' };
    service.create(request).subscribe((result) => expect(result).toEqual(client));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(client);
  });

  it('update(id) patches the exact UpdateClientApiRequest shape', () => {
    const request = { firstName: 'Ada', lastName: 'Byron', email: 'ada@brika.test', phone: '600000000' };
    service.update('c1', request).subscribe((result) => expect(result).toEqual(client));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(client);
  });
});
