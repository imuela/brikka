import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { CompanyService } from './company.service';
import { Company, CompanySubscription } from './company.model';

describe('CompanyService', () => {
  let service: CompanyService;
  let httpMock: HttpTestingController;

  const company: Company = {
    id: 'co1',
    legalName: 'Brika Demo SL',
    tradeName: 'Brika',
    taxId: 'B12345678',
    status: 'ACTIVE',
  };
  const subscription: CompanySubscription = {
    id: 's1',
    companyId: 'co1',
    planId: 'p1',
    status: 'ACTIVE',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CompanyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/companies', () => {
    service.list().subscribe((result) => expect(result).toEqual([company]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`);
    expect(req.request.method).toBe('GET');
    req.flush([company]);
  });

  it('get(id) calls GET /api/v1/companies/{id}', () => {
    service.get('co1').subscribe((result) => expect(result).toEqual(company));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`);
    expect(req.request.method).toBe('GET');
    req.flush(company);
  });

  it('create() POSTs the exact CreateCompanyApiRequest shape', () => {
    const request = { legalName: 'Brika Demo SL', tradeName: 'Brika', taxId: 'B12345678' };
    service.create(request).subscribe((result) => expect(result).toEqual(company));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(company);
  });

  it('update(id) PATCHes legalName/tradeName/taxId', () => {
    const request = { legalName: 'Brika Demo SL', tradeName: 'Brika', taxId: 'B12345678' };
    service.update('co1', request).subscribe((result) => expect(result).toEqual(company));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(company);
  });

  it('suspend(id) POSTs to /suspend', () => {
    service.suspend('co1').subscribe((result) => expect(result).toEqual({ ...company, status: 'SUSPENDED' }));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/suspend`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...company, status: 'SUSPENDED' });
  });

  it('delete(id) sends DELETE', () => {
    service.delete('co1').subscribe((result) => expect(result).toEqual({ ...company, status: 'DELETED' }));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`);
    expect(req.request.method).toBe('DELETE');
    req.flush({ ...company, status: 'DELETED' });
  });

  it('getSubscription(companyId) calls GET .../subscription', () => {
    service.getSubscription('co1').subscribe((result) => expect(result).toEqual(subscription));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`);
    expect(req.request.method).toBe('GET');
    req.flush(subscription);
  });

  it('upsertSubscription(companyId) PUTs the exact UpsertCompanySubscriptionApiRequest shape', () => {
    const request = { planId: 'p1', status: 'ACTIVE' };
    service.upsertSubscription('co1', request).subscribe((result) => expect(result).toEqual(subscription));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(subscription);
  });

  it('cancelSubscription(companyId) POSTs to .../subscription/cancel', () => {
    service
      .cancelSubscription('co1')
      .subscribe((result) => expect(result).toEqual({ ...subscription, status: 'CANCELLED' }));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription/cancel`);
    expect(req.request.method).toBe('POST');
    req.flush({ ...subscription, status: 'CANCELLED' });
  });
});
