import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { PropertyService } from './property.service';
import { Property } from './property.model';

describe('PropertyService', () => {
  let service: PropertyService;
  let httpMock: HttpTestingController;

  const property: Property = {
    id: 'p1',
    companyId: 'co1',
    caseId: 'k1',
    address: { street: 'Calle Mayor 1', city: 'Madrid' },
    propertyType: 'FLAT',
    valuation: 250000,
    purchasePrice: 240000,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PropertyService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('get(caseId) calls GET /api/v1/cases/{caseId}/property', () => {
    service.get('k1').subscribe((result) => expect(result).toEqual(property));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`);
    expect(req.request.method).toBe('GET');
    req.flush(property);
  });

  it('upsert(caseId) PUTs the exact UpsertPropertyApiRequest shape', () => {
    const request = {
      address: { street: 'Calle Mayor 1', city: 'Madrid' },
      propertyType: 'FLAT',
      valuation: 250000,
      purchasePrice: 240000,
    };
    service.upsert('k1', request).subscribe((result) => expect(result).toEqual(property));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush(property);
  });
});
