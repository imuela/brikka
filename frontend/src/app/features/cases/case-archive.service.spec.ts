import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { CaseArchiveService } from './case-archive.service';

describe('CaseArchiveService', () => {
  let service: CaseArchiveService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CaseArchiveService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('downloadArchive(caseId) GETs the archive as a blob with the full response', () => {
    const blob = new Blob(['zip-bytes'], { type: 'application/zip' });
    service.downloadArchive('k1').subscribe((response) => {
      expect(response.body).toBe(blob);
    });

    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/cases/k1/documents/archive`,
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(blob, {
      headers: { 'Content-Disposition': 'attachment; filename="expediente-REF-1-documentos.zip"' },
    });
  });
});
