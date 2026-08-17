import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { CreateDocumentRequestDialogComponent } from './create-document-request-dialog.component';

const documentTypes = [{ id: 't1', code: 'DNI', name: 'DNI', active: true }];
const caseClient = {
  clientId: 'c1',
  firstName: 'Ada',
  lastName: 'Lovelace',
  participationType: 'HOLDER',
  isPrimary: true,
};

describe('CreateDocumentRequestDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [CreateDocumentRequestDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1', documentTypes } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushClients() {
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`).flush([caseClient]);
  }

  it('loads the case clients (not all tenant clients) to populate the picker', () => {
    const fixture = TestBed.createComponent(CreateDocumentRequestDialogComponent);
    fixture.detectChanges();
    flushClients();

    expect(fixture.componentInstance.caseClients()).toEqual([caseClient]);
  });

  it('submits the request with requirementId always null and closes with the created request', () => {
    const fixture = TestBed.createComponent(CreateDocumentRequestDialogComponent);
    fixture.detectChanges();
    flushClients();

    fixture.componentInstance.form.setValue({
      documentTypeId: 't1',
      requestedFromClientId: 'c1',
      dueAt: '2026-09-01',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      documentTypeId: 't1',
      requestedFromClientId: 'c1',
      dueAt: new Date('2026-09-01').toISOString(),
      requirementId: null,
    });
    const response = {
      id: 'dr1',
      companyId: 'co1',
      caseId: 'k1',
      documentTypeId: 't1',
      requestedFromClientId: 'c1',
      status: 'PENDING',
      dueAt: new Date('2026-09-01').toISOString(),
      requestedBy: 'u1',
      requirementId: null,
    };
    req.flush(response);

    expect(dialogRef.close).toHaveBeenCalledWith(response);
  });

  it('does not submit without a selected document type', () => {
    const fixture = TestBed.createComponent(CreateDocumentRequestDialogComponent);
    fixture.detectChanges();
    flushClients();

    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error on failure', () => {
    const fixture = TestBed.createComponent(CreateDocumentRequestDialogComponent);
    fixture.detectChanges();
    flushClients();

    fixture.componentInstance.form.setValue({
      documentTypeId: 't1',
      requestedFromClientId: '',
      dueAt: '',
    });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`)
      .flush({ code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' }, { status: 403, statusText: 'Forbidden' });

    expect(fixture.componentInstance.error()).toBe('Access denied.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('cancel() closes the dialog without a result', () => {
    const fixture = TestBed.createComponent(CreateDocumentRequestDialogComponent);
    fixture.detectChanges();
    flushClients();
    fixture.componentInstance.cancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
