import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { UploadVersionDialogComponent } from './upload-version-dialog.component';

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

describe('UploadVersionDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [UploadVersionDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { documentId: 'd1' } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('does not submit without a selected file', () => {
    const fixture = TestBed.createComponent(UploadVersionDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`);
  });

  it('uploads the selected file as multipart and closes the dialog with the new version', () => {
    const fixture = TestBed.createComponent(UploadVersionDialogComponent);
    fixture.detectChanges();

    const file = new File(['content'], 'dni.pdf', { type: 'application/pdf' });
    fixture.componentInstance.file.set(file);
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`);
    expect(req.request.method).toBe('POST');
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush(version);

    expect(dialogRef.close).toHaveBeenCalledWith(version);
  });

  it('shows the backend error on failure', () => {
    const fixture = TestBed.createComponent(UploadVersionDialogComponent);
    fixture.detectChanges();

    const file = new File(['content'], 'dni.exe', { type: 'application/x-msdownload' });
    fixture.componentInstance.file.set(file);
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/versions`)
      .flush({ code: 'UNSUPPORTED_MIME_TYPE', message: 'Unsupported file type.', requestId: 'r1' }, { status: 400, statusText: 'Bad Request' });

    expect(fixture.componentInstance.error()).toBe('Unsupported file type.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('cancel() closes the dialog without a result', () => {
    const fixture = TestBed.createComponent(UploadVersionDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
