import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { VersionsDialogComponent } from './versions-dialog.component';

const version = {
  id: 'v1',
  documentId: 'd1',
  versionNumber: 1,
  originalFilename: 'dni.pdf',
  mimeType: 'application/pdf',
  sizeBytes: 10,
  checksum: 'abc',
  uploadedBy: 'u1',
  uploadedAt: '2026-08-17T10:00:00Z',
  reviewStatus: 'PENDING',
  reviewedBy: null,
  reviewedAt: null,
  reviewComment: null,
};

describe('VersionsDialogComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;
  let dialogRef: { close: ReturnType<typeof vi.fn> };
  let openSpy: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    openSpy = vi.fn();
    vi.stubGlobal('open', openSpy);
    TestBed.configureTestingModule({
      imports: [VersionsDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { documentId: 'd1' } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
  });

  afterEach(() => {
    httpMock.verify();
    vi.unstubAllGlobals();
  });

  function flushInitialLoad(versions: unknown[] = [version], extractions: unknown[] = []) {
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`).flush(versions);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/ai/document-extractions`)
      .flush(extractions);
  }

  it('loads and renders the versions of the document on init', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('dni.pdf');
  });

  it('download() fetches a presigned URL and opens it', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    flushInitialLoad();

    fixture.componentInstance.download('v1');
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions/v1/download`)
      .flush({ url: 'https://storage.test/presigned', expiresInSeconds: 60 });

    expect(openSpy).toHaveBeenCalledWith('https://storage.test/presigned', '_blank');
  });

  it('shows the backend error when loading versions fails', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`)
      .flush({ code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' }, { status: 403, statusText: 'Forbidden' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/ai/document-extractions`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No tienes permisos para realizar esta acción.');
  });

  it('close() closes the dialog', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    flushInitialLoad([]);
    fixture.componentInstance.close();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });

  it('the AI section and "Analizar con IA" button are gated by AI_DOCUMENT_ANALYZE', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Análisis con IA');
    expect(fixture.nativeElement.textContent).not.toContain('Analizar con IA');

    sessionStore.setPermissions(['AI_DOCUMENT_ANALYZE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Análisis con IA');
    expect(fixture.nativeElement.textContent).toContain('Analizar con IA');
    expect(fixture.nativeElement.textContent).toContain('Sin análisis todavía.');
    expect(fixture.nativeElement.textContent).toContain(
      'Resultado generado automáticamente. Debe ser revisado y validado por un usuario.',
    );
  });

  it('analyze() posts the request and reloads the extraction list', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    flushInitialLoad();
    sessionStore.setPermissions(['AI_DOCUMENT_ANALYZE']);
    fixture.detectChanges();

    fixture.componentInstance.analyze('v1');
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/ai/document-extractions`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ documentVersionId: 'v1' });
    req.flush({
      id: 'e1',
      documentVersionId: 'v1',
      status: 'NO_PROVIDER',
      provider: 'none',
      model: 'none',
      extractedData: [],
      confidence: {},
      validatedBy: null,
      validatedAt: null,
      createdAt: '2026-08-22T10:00:00Z',
    });

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/ai/document-extractions`)
      .flush([
        {
          id: 'e1',
          documentVersionId: 'v1',
          status: 'NO_PROVIDER',
          provider: 'none',
          model: 'none',
          extractedData: [],
          confidence: {},
          validatedBy: null,
          validatedAt: null,
          createdAt: '2026-08-22T10:00:00Z',
        },
      ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin proveedor de IA configurado');
  });

  it('renders a COMPLETED result with fields, summary and an inconsistency', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    flushInitialLoad([version], [
      {
        id: 'e2',
        documentVersionId: 'v1',
        status: 'COMPLETED',
        provider: 'anthropic',
        model: 'claude-3-5-sonnet-20241022',
        extractedData: {
          fields: [{ name: 'monthly_income', value: '1900', confidence: 0.9, page: 1 }],
          summary: 'Payslip for Javier Ruiz.',
          warnings: [],
          inconsistencies: [
            { field: 'monthly_income', clientId: 'c1', profileValue: 3000, documentValue: 1900 },
          ],
        },
        confidence: { overall: 0.9 },
        validatedBy: null,
        validatedAt: null,
        createdAt: '2026-08-22T10:00:00Z',
      },
    ]);
    sessionStore.setPermissions(['AI_DOCUMENT_ANALYZE']);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Payslip for Javier Ruiz.');
    expect(text).toContain('monthly_income');
    expect(text).toContain('1900');
    expect(text).toContain('Inconsistencias detectadas');
    expect(text).toContain('3000');
  });

  it('shows the structured backend error when the analysis request fails', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    flushInitialLoad();
    sessionStore.setPermissions(['AI_DOCUMENT_ANALYZE']);
    fixture.detectChanges();

    fixture.componentInstance.analyze('v1');
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/ai/document-extractions`)
      .flush(
        { code: 'DOCUMENT_VERSION_NOT_IN_DOCUMENT', message: 'x', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.aiError()).toContain('no pertenece a este documento');
  });
});
