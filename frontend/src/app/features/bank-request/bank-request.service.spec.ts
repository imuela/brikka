import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { BankRequestService } from './bank-request.service';
import { BankOffer, BankRequest, BankResponseRecord, FinalFinancing } from './bank-request.model';

describe('BankRequestService', () => {
  let service: BankRequestService;
  let httpMock: HttpTestingController;

  const bankRequest: BankRequest = {
    id: 'br1',
    caseId: 'k1',
    bankId: 'b1',
    bankContactId: null,
    status: 'SENT',
    submittedAt: '2026-08-18T10:00:00Z',
    contactSnapshot: {},
    createdAt: '2026-08-18T10:00:00Z',
    updatedAt: '2026-08-18T10:00:00Z',
  };

  const bankResponse: BankResponseRecord = {
    id: 'bres1',
    bankRequestId: 'br1',
    status: 'RECEIVED',
    receivedAt: '2026-08-18T10:01:00Z',
    summary: 'Aprobado en condiciones estándar.',
    payload: {},
    createdAt: '2026-08-18T10:01:00Z',
  };

  const offer: BankOffer = {
    id: 'off1',
    bankRequestId: 'br1',
    bankId: 'b1',
    status: 'RECEIVED',
    amount: 180000,
    interestRate: 3.2,
    termMonths: 300,
    payment: 870.5,
    conditions: {},
    receivedAt: '2026-08-18T10:02:00Z',
    createdAt: '2026-08-18T10:02:00Z',
    updatedAt: '2026-08-18T10:02:00Z',
  };

  const finalFinancing: FinalFinancing = {
    id: 'ff1',
    caseId: 'k1',
    bankOfferId: 'off1',
    status: 'ACTIVE',
    finalizedAt: '2026-08-18T10:03:00Z',
    createdAt: '2026-08-18T10:03:00Z',
    updatedAt: '2026-08-18T10:03:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BankRequestService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list(caseId) calls GET /api/v1/cases/{caseId}/bank-requests', () => {
    service.list('k1').subscribe((res) => expect(res).toEqual([bankRequest]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`);
    expect(req.request.method).toBe('GET');
    req.flush([bankRequest]);
  });

  it('create(caseId) POSTs the exact CreateBankRequestApiRequest shape', () => {
    const request = { bankId: 'b1', bankContactId: null };
    service.create('k1', request).subscribe((res) => expect(res).toEqual(bankRequest));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(bankRequest);
  });

  it('get(id) calls GET /api/v1/bank-requests/{id}', () => {
    service.get('br1').subscribe((res) => expect(res).toEqual(bankRequest));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/bank-requests/br1`);
    expect(req.request.method).toBe('GET');
    req.flush(bankRequest);
  });

  it('createResponse(bankRequestId) POSTs the exact CreateBankResponseApiRequest shape', () => {
    const request = { summary: 'Aprobado en condiciones estándar.', payload: {} };
    service
      .createResponse('br1', request)
      .subscribe((res) => expect(res).toEqual(bankResponse));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/bank-requests/br1/responses`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(bankResponse);
  });

  it('createOffer(bankRequestId) POSTs the exact CreateBankOfferApiRequest shape', () => {
    const request = { amount: 180000, interestRate: 3.2, termMonths: 300, payment: 870.5, conditions: {} };
    service.createOffer('br1', request).subscribe((res) => expect(res).toEqual(offer));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/bank-requests/br1/offers`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(offer);
  });

  it('listOffers(caseId) calls GET /api/v1/cases/{caseId}/offers', () => {
    service.listOffers('k1').subscribe((res) => expect(res).toEqual([offer]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/offers`);
    expect(req.request.method).toBe('GET');
    req.flush([offer]);
  });

  it('getOffer(id) calls GET /api/v1/bank-offers/{id}', () => {
    service.getOffer('off1').subscribe((res) => expect(res).toEqual(offer));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/bank-offers/off1`);
    expect(req.request.method).toBe('GET');
    req.flush(offer);
  });

  it('selectOffer(id) calls POST /api/v1/bank-offers/{id}/select', () => {
    service.selectOffer('off1').subscribe((res) => expect(res).toEqual(finalFinancing));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/bank-offers/off1/select`);
    expect(req.request.method).toBe('POST');
    req.flush(finalFinancing);
  });
});
