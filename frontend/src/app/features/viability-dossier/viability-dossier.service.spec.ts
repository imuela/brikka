import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { ViabilityDossierService } from './viability-dossier.service';
import { CaseNarrative } from './viability-dossier.model';

describe('ViabilityDossierService', () => {
  let service: ViabilityDossierService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ViabilityDossierService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('getNarrative(caseId) calls GET /api/v1/cases/{caseId}/dossier/narrative', () => {
    const narrative: CaseNarrative = {
      sections: [{ key: 'situation', title: 'Situación del expediente', paragraphs: ['...'] }],
    };
    service.getNarrative('k1').subscribe((result) => expect(result).toEqual(narrative));
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/cases/k1/dossier/narrative`,
    );
    expect(req.request.method).toBe('GET');
    req.flush(narrative);
  });

  it('generate(caseId) POSTs to /api/v1/cases/{caseId}/dossier', () => {
    service.generate('k1').subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/dossier`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'v1' });
  });
});
