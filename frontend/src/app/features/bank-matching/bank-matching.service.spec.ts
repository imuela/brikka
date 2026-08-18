import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { BankMatchingService } from './bank-matching.service';
import { BankMatchResult, BankMatchRuleOverride } from './bank-matching.model';

describe('BankMatchingService', () => {
  let service: BankMatchingService;
  let httpMock: HttpTestingController;

  const result: BankMatchResult = {
    id: 'm1',
    caseId: 'k1',
    bankId: 'b1',
    bankCriteriaVersionId: 'c1',
    globalResult: 'PASS',
    effectiveGlobalResult: 'PASS',
    evaluatedAt: '2026-08-18T10:00:00Z',
    inputSnapshot: { ltv: 0.5 },
    ruleResults: [],
  };

  const override: BankMatchRuleOverride = {
    id: 'o1',
    previousResult: 'FAIL',
    newResult: 'PASS',
    reason: 'Excepción autorizada por el manager.',
    overriddenBy: 'u1',
    overriddenAt: '2026-08-18T10:05:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BankMatchingService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('run(caseId, bankId) calls POST /api/v1/cases/{caseId}/banks/{bankId}/matching', () => {
    service.run('k1', 'b1').subscribe((res) => expect(res).toEqual(result));
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/cases/k1/banks/b1/matching`,
    );
    expect(req.request.method).toBe('POST');
    req.flush(result);
  });

  it('list(caseId) calls GET /api/v1/cases/{caseId}/matching', () => {
    service.list('k1').subscribe((res) => expect(res).toEqual([result]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/matching`);
    expect(req.request.method).toBe('GET');
    req.flush([result]);
  });

  it('createOverride(ruleResultId) POSTs the exact CreateBankMatchRuleOverrideApiRequest shape', () => {
    const request = { previousResult: 'FAIL', newResult: 'PASS', reason: 'Excepción autorizada por el manager.' };
    service.createOverride('rr1', request).subscribe((res) => expect(res).toEqual(override));
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/bank-match-rule-results/rr1/overrides`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(override);
  });
});
