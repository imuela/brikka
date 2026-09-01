import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { DocumentsService } from './documents.service';
import {
  CaseDocument,
  CaseDocumentPublication,
  CaseDocumentRequest,
  CaseDocumentVersion,
  DocumentType,
} from './document.model';

describe('DocumentsService', () => {
  let service: DocumentsService;
  let httpMock: HttpTestingController;

  const documentType: DocumentType = { id: 't1', code: 'DNI', name: 'DNI', active: true };

  const document: CaseDocument = {
    id: 'd1',
    companyId: 'co1',
    caseId: 'k1',
    documentTypeId: 't1',
    currentVersionId: 'v1',
    status: 'PENDING',
  };

  const version: CaseDocumentVersion = {
    id: 'v1',
    documentId: 'd1',
    versionNumber: 1,
    originalFilename: 'dni.pdf',
    mimeType: 'application/pdf',
    sizeBytes: 1024,
    checksum: 'abc123',
    uploadedBy: 'u1',
    uploadedAt: '2026-08-17T10:00:00Z',
    reviewStatus: 'PENDING',
    reviewedBy: null,
    reviewedAt: null,
    reviewComment: null,
  };

  const publication: CaseDocumentPublication = {
    id: 'pub1',
    documentId: 'd1',
    documentVersionId: 'v1',
    publishedToPortal: true,
    publishedAt: '2026-08-17T10:05:00Z',
  };

  const documentRequest: CaseDocumentRequest = {
    id: 'dr1',
    companyId: 'co1',
    caseId: 'k1',
    documentTypeId: 't1',
    requestedFromClientId: 'c1',
    status: 'PENDING',
    dueAt: null,
    requestedBy: 'u1',
    requirementId: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DocumentsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listDocumentTypes() calls GET /api/v1/document-types', () => {
    service.listDocumentTypes().subscribe((types) => expect(types).toEqual([documentType]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-types`);
    expect(req.request.method).toBe('GET');
    req.flush([documentType]);
  });

  it('list(caseId) calls GET /api/v1/cases/{caseId}/documents', () => {
    service.list('k1').subscribe((docs) => expect(docs).toEqual([document]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`);
    expect(req.request.method).toBe('GET');
    req.flush([document]);
  });

  it('create() posts the exact CreateDocumentApiRequest shape', () => {
    const request = { documentTypeId: 't1' };
    service.create('k1', request).subscribe((result) => expect(result).toEqual(document));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(document);
  });

  it('listVersions(id) calls GET /api/v1/documents/{id}/versions', () => {
    service.listVersions('d1').subscribe((versions) => expect(versions).toEqual([version]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`);
    expect(req.request.method).toBe('GET');
    req.flush([version]);
  });

  it('uploadVersion() posts a multipart FormData with the file under the "file" field', () => {
    const file = new File(['content'], 'dni.pdf', { type: 'application/pdf' });
    service.uploadVersion('d1', file).subscribe((result) => expect(result).toEqual(version));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(version);
  });

  it('review() posts the exact ReviewDocumentApiRequest shape', () => {
    const request = { decision: 'APPROVED', comment: '' };
    service.review('d1', request).subscribe((result) => expect(result).toEqual(version));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/review`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(version);
  });

  it('publish(id) calls POST /api/v1/documents/{id}/publish', () => {
    service.publish('d1').subscribe((result) => expect(result).toEqual(publication));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/publish`);
    expect(req.request.method).toBe('POST');
    req.flush(publication);
  });

  it('unpublish(id) calls POST /api/v1/documents/{id}/unpublish', () => {
    service.unpublish('d1').subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/unpublish`);
    expect(req.request.method).toBe('POST');
    req.flush(null);
  });

  it('downloadCurrent(id) calls GET /api/v1/documents/{id}/download', () => {
    service
      .downloadCurrent('d1')
      .subscribe((result) => expect(result).toEqual({ url: 'https://x/y', expiresInSeconds: 60 }));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/download`);
    expect(req.request.method).toBe('GET');
    req.flush({ url: 'https://x/y', expiresInSeconds: 60 });
  });

  it('downloadVersion(id, versionId) calls GET /api/v1/documents/{id}/versions/{versionId}/download', () => {
    service.downloadVersion('d1', 'v1').subscribe();
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/documents/d1/versions/v1/download`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({ url: 'https://x/y', expiresInSeconds: 60 });
  });

  it('listRequests(caseId) calls GET /api/v1/cases/{caseId}/document-requests', () => {
    service.listRequests('k1').subscribe((requests) => expect(requests).toEqual([documentRequest]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`);
    expect(req.request.method).toBe('GET');
    req.flush([documentRequest]);
  });

  it('createRequest() posts the exact CreateDocumentRequestApiRequest shape', () => {
    const request = {
      documentTypeId: 't1',
      requestedFromClientId: 'c1',
      dueAt: null,
      requirementId: null,
    };
    service
      .createRequest('k1', request)
      .subscribe((result) => expect(result).toEqual(documentRequest));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(documentRequest);
  });

  it('updateRequest() patches the exact UpdateDocumentRequestApiRequest shape', () => {
    const request = { status: 'FULFILLED' };
    service
      .updateRequest('dr1', request)
      .subscribe((result) => expect(result).toEqual(documentRequest));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-requests/dr1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(documentRequest);
  });

  it('getChecklist(caseId) calls GET /api/v1/cases/{caseId}/checklist', () => {
    const checklist = {
      mandatoryTotal: 2,
      mandatoryMissing: 1,
      optionalTotal: 0,
      optionalMissing: 0,
      complete: false,
      items: [],
    };
    service.getChecklist('k1').subscribe((result) => expect(result).toEqual(checklist));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/checklist`);
    expect(req.request.method).toBe('GET');
    req.flush(checklist);
  });
});
