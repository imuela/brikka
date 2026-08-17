import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
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
  });

  afterEach(() => {
    httpMock.verify();
    vi.unstubAllGlobals();
  });

  it('loads and renders the versions of the document on init', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`).flush([version]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('dni.pdf');
  });

  it('download() fetches a presigned URL and opens it', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`).flush([version]);

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
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Access denied.');
  });

  it('close() closes the dialog', () => {
    const fixture = TestBed.createComponent(VersionsDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`).flush([]);
    fixture.componentInstance.close();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
