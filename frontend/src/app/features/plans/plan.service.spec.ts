import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { PlanService } from './plan.service';
import { Plan } from './plan.model';

describe('PlanService', () => {
  let service: PlanService;
  let httpMock: HttpTestingController;

  const plan: Plan = { id: 'p1', code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlanService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/plans', () => {
    service.list().subscribe((result) => expect(result).toEqual([plan]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`);
    expect(req.request.method).toBe('GET');
    req.flush([plan]);
  });

  it('get(id) calls GET /api/v1/plans/{id}', () => {
    service.get('p1').subscribe((result) => expect(result).toEqual(plan));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans/p1`);
    expect(req.request.method).toBe('GET');
    req.flush(plan);
  });

  it('create() POSTs the exact CreatePlanApiRequest shape', () => {
    const request = { code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' };
    service.create(request).subscribe((result) => expect(result).toEqual(plan));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(plan);
  });

  it('update(id) PATCHes name/status', () => {
    const request = { name: 'Plan Pro', status: 'ACTIVE' };
    service.update('p1', request).subscribe((result) => expect(result).toEqual(plan));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans/p1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(plan);
  });
});
