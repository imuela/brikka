import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { PortalDocumentService } from './portal-document.service';
import { PortalDocument, PortalDocumentRequest } from './portal-document.model';

describe('PortalDocumentService', () => {
  let service: PortalDocumentService;
  let httpMock: HttpTestingController;

  const document: PortalDocument = {
    id: 'd1',
    documentTypeId: 'dt1',
    versionNumber: 1,
    originalFilename: 'dni.pdf',
    publishedAt: '2026-08-18T10:00:00Z',
  };
  const request: PortalDocumentRequest = {
    id: 'dr1',
    documentTypeId: 'dt1',
    documentTypeCode: 'DNI',
    documentTypeName: 'DNI',
    status: 'PENDING',
    dueAt: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortalDocumentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list(caseId) calls GET /api/v1/portal/cases/{id}/documents', () => {
    service.list('k1').subscribe((result) => expect(result).toEqual([document]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/documents`);
    expect(req.request.method).toBe('GET');
    req.flush([document]);
  });

  it('upload() POSTs multipart form data with documentTypeId and file', () => {
    const file = new File(['content'], 'dni.pdf', { type: 'application/pdf' });
    service.upload('k1', 'dt1', file).subscribe((result) => expect(result).toEqual(document));

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/documents`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    const body = req.request.body as FormData;
    expect(body.get('documentTypeId')).toBe('dt1');
    expect(body.get('file')).toBe(file);
    req.flush(document);
  });

  it('listDocumentRequests(caseId) calls GET /api/v1/portal/cases/{id}/document-requests', () => {
    service
      .listDocumentRequests('k1')
      .subscribe((result) => expect(result).toEqual([request]));
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/portal/cases/k1/document-requests`,
    );
    expect(req.request.method).toBe('GET');
    req.flush([request]);
  });
});
