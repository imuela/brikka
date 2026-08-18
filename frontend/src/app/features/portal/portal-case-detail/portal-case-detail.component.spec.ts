import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { PortalCaseDetailComponent } from './portal-case-detail.component';

const theCase = {
  id: 'k1',
  reference: 'REF-1',
  status: 'PRESTUDY',
  operationType: 'MORTGAGE',
  createdAt: '2026-08-18T10:00:00Z',
};
const document = {
  id: 'd1',
  documentTypeId: 'dt1',
  versionNumber: 1,
  originalFilename: 'dni.pdf',
  publishedAt: '2026-08-18T10:00:00Z',
};
const request = {
  id: 'dr1',
  documentTypeId: 'dt1',
  documentTypeCode: 'DNI',
  documentTypeName: 'DNI',
  status: 'PENDING',
  dueAt: null,
};
const message = {
  id: 'm1',
  conversationId: 'c1',
  senderUserId: 'u1',
  senderClientId: null,
  body: 'Hello client',
  createdAt: '2026-08-18T10:00:00Z',
  editedAt: null,
};

function configure() {
  TestBed.configureTestingModule({
    imports: [PortalCaseDetailComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ id: 'k1' }) } },
      },
    ],
  });
}

function flushInitialLoad(httpMock: HttpTestingController) {
  httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1`).flush(theCase);
  httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/documents`).flush([document]);
  httpMock
    .expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/document-requests`)
    .flush([request]);
  httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/messages`).flush([message]);
}

describe('PortalCaseDetailComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('loads and renders the case, documents, document requests and messages', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PortalCaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad(httpMock);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('REF-1');
    expect(fixture.nativeElement.textContent).toContain('dni.pdf');
    expect(fixture.nativeElement.textContent).toContain('DNI');
    expect(fixture.nativeElement.textContent).toContain('Subir documento');
    expect(fixture.nativeElement.textContent).toContain('Hello client');
  });

  it('uploadForRequest uploads using the request documentTypeId then reloads documents and requests', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PortalCaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad(httpMock);
    fixture.detectChanges();

    const file = new File(['content'], 'dni.pdf', { type: 'application/pdf' });
    const event = { target: { files: [file], value: '' } } as unknown as Event;
    fixture.componentInstance.uploadForRequest(request, event);

    const uploadReq = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/portal/cases/k1/documents`,
    );
    expect(uploadReq.request.method).toBe('POST');
    const body = uploadReq.request.body as FormData;
    expect(body.get('documentTypeId')).toBe('dt1');
    uploadReq.flush(document);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/documents`).flush([document]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/document-requests`)
      .flush([{ ...request, status: 'FULFILLED' }]);
  });

  it('sendMessage posts the message body then reloads messages', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PortalCaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad(httpMock);
    fixture.detectChanges();

    fixture.componentInstance.messageForm.setValue({ body: 'Thanks' });
    fixture.componentInstance.sendMessage();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/messages`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'Thanks' });
    req.flush(message);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/messages`).flush([message]);
  });

  it('toggleAttachments lazily loads attachments for a message', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PortalCaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad(httpMock);
    fixture.detectChanges();

    fixture.componentInstance.toggleAttachments(message);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/messages/m1/attachments`)
      .flush([]);

    expect(fixture.componentInstance.attachmentsByMessage()['m1']).toEqual([]);
  });

  it('shows the backend error message when a load fails', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PortalCaseDetailComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1`)
      .flush(
        { code: 'CASE_NOT_FOUND', message: 'not found', requestId: 'r1' },
        { status: 404, statusText: 'Not Found' },
      );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/documents`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/document-requests`)
      .flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/messages`).flush([]);
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBe('No se ha encontrado la operación solicitada.');
  });
});
