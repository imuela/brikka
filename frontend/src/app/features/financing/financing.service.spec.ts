import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { FinancingService } from './financing.service';
import { CreateSimulationRequest, FinancingRequest, Simulation } from './financing.model';

describe('FinancingService', () => {
  let service: FinancingService;
  let httpMock: HttpTestingController;

  const simulation: Simulation = {
    id: 's1',
    caseId: 'k1',
    principal: 200000,
    interestRate: 3.2,
    termMonths: 300,
    estimatedPayment: 969.32,
    interestType: 'FIXED',
    baseInterestRate: 3.5,
    finalInterestRate: 3.2,
    euriborRate: null,
    spreadRate: null,
    fixedPeriodMonths: null,
    fixedPeriodRate: null,
    icoGuarantee: false,
    bonifications: [{ code: 'PAYROLL', label: 'Domiciliación de nómina', rate: 0.3, active: true }],
    variablePhase: null,
    metadata: {},
    createdBy: 'u1',
    createdAt: '2026-08-17T10:00:00Z',
  };

  const financingRequest: FinancingRequest = {
    id: 'fr1',
    caseId: 'k1',
    status: 'PENDING',
    requestedAmount: 180000,
    termMonths: 300,
    createdAt: '2026-08-17T10:00:00Z',
    updatedAt: '2026-08-17T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FinancingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listSimulations(caseId) calls GET /api/v1/cases/{caseId}/simulations', () => {
    service.listSimulations('k1').subscribe((result) => expect(result).toEqual([simulation]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`);
    expect(req.request.method).toBe('GET');
    req.flush([simulation]);
  });

  it('createSimulation(caseId) POSTs the exact CreateSimulationApiRequest shape', () => {
    const request: CreateSimulationRequest = {
      interestType: 'FIXED',
      principal: 200000,
      termMonths: 300,
      fixedRate: 3.5,
      euriborRate: null,
      spreadRate: null,
      fixedPeriodMonths: null,
      fixedPeriodRate: null,
      bonifications: [
        { code: 'PAYROLL', label: 'Domiciliación de nómina', rate: 0.3, active: true },
      ],
      icoGuarantee: false,
      metadata: {},
    };
    service
      .createSimulation('k1', request)
      .subscribe((result) => expect(result).toEqual(simulation));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(simulation);
  });

  it('listFinancingRequests(caseId) calls GET /api/v1/cases/{caseId}/financing-requests', () => {
    service
      .listFinancingRequests('k1')
      .subscribe((result) => expect(result).toEqual([financingRequest]));
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`,
    );
    expect(req.request.method).toBe('GET');
    req.flush([financingRequest]);
  });

  it('createFinancingRequest(caseId) POSTs the exact CreateFinancingRequestApiRequest shape', () => {
    const request = { requestedAmount: 180000, termMonths: 300 };
    service
      .createFinancingRequest('k1', request)
      .subscribe((result) => expect(result).toEqual(financingRequest));
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(financingRequest);
  });

  it('updateFinancingRequest(id) PATCHes the exact UpdateFinancingRequestApiRequest shape', () => {
    const request = { status: 'IN_PROGRESS', requestedAmount: 180000, termMonths: 300 };
    service
      .updateFinancingRequest('fr1', request)
      .subscribe((result) => expect(result).toEqual(financingRequest));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/financing-requests/fr1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(financingRequest);
  });
});
