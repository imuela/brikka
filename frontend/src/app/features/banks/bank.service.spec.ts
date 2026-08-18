import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { BankService } from './bank.service';
import { Bank, BankCriteriaVersion, BankProduct } from './bank.model';

describe('BankService', () => {
  let service: BankService;
  let httpMock: HttpTestingController;

  const bank: Bank = { id: 'b1', code: 'DEVBANK', name: 'Banco Demo Desarrollo', status: 'ACTIVE', metadata: {} };
  const product: BankProduct = {
    id: 'p1',
    bankId: 'b1',
    code: 'HIP-30',
    name: 'Hipoteca 30 años',
    status: 'ACTIVE',
    metadata: {},
  };
  const criteria: BankCriteriaVersion = {
    id: 'c1',
    bankId: 'b1',
    version: 'v1',
    status: 'ACTIVE',
    effectiveFrom: '2026-08-18T00:00:00Z',
    effectiveTo: null,
    rules: { rules: [] },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BankService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/banks', () => {
    service.list().subscribe((result) => expect(result).toEqual([bank]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`);
    expect(req.request.method).toBe('GET');
    req.flush([bank]);
  });

  it('get(id) calls GET /api/v1/banks/{id}', () => {
    service.get('b1').subscribe((result) => expect(result).toEqual(bank));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1`);
    expect(req.request.method).toBe('GET');
    req.flush(bank);
  });

  it('create() POSTs the exact CreateBankApiRequest shape', () => {
    const request = { code: 'DEVBANK', name: 'Banco Demo Desarrollo', metadata: {} };
    service.create(request).subscribe((result) => expect(result).toEqual(bank));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(bank);
  });

  it('listProducts(bankId) calls GET /api/v1/banks/{id}/products', () => {
    service.listProducts('b1').subscribe((result) => expect(result).toEqual([product]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/products`);
    expect(req.request.method).toBe('GET');
    req.flush([product]);
  });

  it('createProduct(bankId) POSTs the exact CreateBankProductApiRequest shape', () => {
    const request = { code: 'HIP-30', name: 'Hipoteca 30 años', metadata: {} };
    service.createProduct('b1', request).subscribe((result) => expect(result).toEqual(product));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/products`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(product);
  });

  it('listCriteria(bankId) calls GET /api/v1/banks/{id}/criteria', () => {
    service.listCriteria('b1').subscribe((result) => expect(result).toEqual([criteria]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/criteria`);
    expect(req.request.method).toBe('GET');
    req.flush([criteria]);
  });

  it('createCriteria(bankId) POSTs the exact CreateBankCriteriaVersionApiRequest shape', () => {
    const request = {
      version: 'v1',
      effectiveFrom: '2026-08-18T00:00:00Z',
      effectiveTo: null,
      rules: { rules: [] },
    };
    service.createCriteria('b1', request).subscribe((result) => expect(result).toEqual(criteria));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/criteria`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(criteria);
  });
});
