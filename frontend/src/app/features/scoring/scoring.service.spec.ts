import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { ScoringService } from './scoring.service';
import { CaseRag } from './scoring.model';

describe('ScoringService', () => {
  let service: ScoringService;
  let httpMock: HttpTestingController;

  const rag: CaseRag = {
    rag: 'AMBER',
    axes: [
      { axis: 'scoring', level: 'GREEN', detail: 'Categoría GREEN (puntuación 100.00)' },
      { axis: 'viability', level: 'AMBER', detail: 'Viabilidad REVISAR' },
      { axis: 'documentation', level: 'NOT_EVALUATED', detail: 'Sin requisitos documentales' },
    ],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ScoringService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getRag(caseId) calls GET /api/v1/cases/{caseId}/scoring/rag', () => {
    service.getRag('k1').subscribe((result) => expect(result).toEqual(rag));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/scoring/rag`);
    expect(req.request.method).toBe('GET');
    req.flush(rag);
  });

  it('run(caseId) POSTs to the existing /api/v1/cases/{caseId}/scoring/run endpoint', () => {
    service.run('k1').subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/scoring/run`);
    expect(req.request.method).toBe('POST');
    req.flush([{ id: 'sr1' }]);
  });
});
