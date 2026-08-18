import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { PortalCaseService } from './portal-case.service';
import { PortalCase } from './portal-case.model';

describe('PortalCaseService', () => {
  let service: PortalCaseService;
  let httpMock: HttpTestingController;

  const theCase: PortalCase = {
    id: 'k1',
    reference: 'REF-1',
    status: 'PRESTUDY',
    operationType: 'MORTGAGE',
    createdAt: '2026-08-18T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortalCaseService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/portal/cases', () => {
    service.list().subscribe((result) => expect(result).toEqual([theCase]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases`);
    expect(req.request.method).toBe('GET');
    req.flush([theCase]);
  });

  it('get(id) calls GET /api/v1/portal/cases/{id}', () => {
    service.get('k1').subscribe((result) => expect(result).toEqual(theCase));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1`);
    expect(req.request.method).toBe('GET');
    req.flush(theCase);
  });
});
