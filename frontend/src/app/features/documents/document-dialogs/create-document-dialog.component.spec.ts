import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { CreateDocumentDialogComponent } from './create-document-dialog.component';

const documentTypes = [{ id: 't1', code: 'DNI', name: 'DNI', active: true }];

describe('CreateDocumentDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [CreateDocumentDialogComponent, NoopAnimationsModule],
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

  it('exposes the document types passed in dialog data', () => {
    const fixture = TestBed.createComponent(CreateDocumentDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.documentTypes).toEqual(documentTypes);
  });

  it('submits documentTypeId and closes the dialog with the created document', () => {
    const fixture = TestBed.createComponent(CreateDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ documentTypeId: 't1' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ documentTypeId: 't1' });
    req.flush({ id: 'd1', companyId: 'co1', caseId: 'k1', documentTypeId: 't1', currentVersionId: null, status: 'PENDING' });

    expect(dialogRef.close).toHaveBeenCalledWith({
      id: 'd1',
      companyId: 'co1',
      caseId: 'k1',
      documentTypeId: 't1',
      currentVersionId: null,
      status: 'PENDING',
    });
  });

  it('does not submit without a selected document type', () => {
    const fixture = TestBed.createComponent(CreateDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error on failure', () => {
    const fixture = TestBed.createComponent(CreateDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ documentTypeId: 't1' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`)
      .flush({ code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' }, { status: 403, statusText: 'Forbidden' });

    expect(fixture.componentInstance.error()).toBe('Access denied.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('cancel() closes the dialog without a result', () => {
    const fixture = TestBed.createComponent(CreateDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
