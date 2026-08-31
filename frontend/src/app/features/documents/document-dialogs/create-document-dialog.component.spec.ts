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
  // Mutable so a test can add `holders` before it creates the component (the provider keeps this
  // object by reference); overrideProvider can't be used once TestBed has been instantiated.
  let dialogData: { caseId: string; documentTypes: typeof documentTypes; holders?: { id: string; name: string }[] };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    dialogData = { caseId: 'k1', documentTypes };
    TestBed.configureTestingModule({
      imports: [CreateDocumentDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: dialogData },
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

  it('submits documentTypeId (clientId null when no holder chosen) and closes with the document', () => {
    const fixture = TestBed.createComponent(CreateDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ documentTypeId: 't1', clientId: '' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ documentTypeId: 't1', clientId: null });
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
    fixture.componentInstance.form.setValue({ documentTypeId: 't1', clientId: '' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`)
      .flush({ code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' }, { status: 403, statusText: 'Forbidden' });

    expect(fixture.componentInstance.error()).toBe('No tienes permisos para realizar esta acción.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('cancel() closes the dialog without a result', () => {
    const fixture = TestBed.createComponent(CreateDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('with case holders, exposes them and submits the chosen clientId (BRIKKA V2 I1)', () => {
    const holders = [
      { id: 'h1', name: 'Ada Lovelace' },
      { id: 'h2', name: 'Alan Turing' },
    ];
    dialogData.holders = holders;

    const fixture = TestBed.createComponent(CreateDocumentDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.holders).toEqual(holders);

    fixture.componentInstance.form.setValue({ documentTypeId: 't1', clientId: 'h2' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`);
    expect(req.request.body).toEqual({ documentTypeId: 't1', clientId: 'h2' });
    req.flush({
      id: 'd1',
      companyId: 'co1',
      caseId: 'k1',
      documentTypeId: 't1',
      clientId: 'h2',
      currentVersionId: null,
      status: 'PENDING',
    });
    expect(dialogRef.close).toHaveBeenCalled();
  });
});
