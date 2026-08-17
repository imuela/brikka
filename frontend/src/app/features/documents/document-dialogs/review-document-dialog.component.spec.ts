import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { ReviewDocumentDialogComponent } from './review-document-dialog.component';

describe('ReviewDocumentDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ReviewDocumentDialogComponent, NoopAnimationsModule],
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

  it('submits decision/comment and closes the dialog with the reviewed version', () => {
    const fixture = TestBed.createComponent(ReviewDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ decision: 'APPROVED', comment: 'Looks good' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/review`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ decision: 'APPROVED', comment: 'Looks good' });
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
      reviewStatus: 'APPROVED',
      reviewedBy: 'u2',
      reviewedAt: '2026-08-17T11:00:00Z',
      reviewComment: 'Looks good',
    };
    req.flush(version);

    expect(dialogRef.close).toHaveBeenCalledWith(version);
  });

  it('does not submit without a selected decision', () => {
    const fixture = TestBed.createComponent(ReviewDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/documents/d1/review`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error on failure', () => {
    const fixture = TestBed.createComponent(ReviewDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ decision: 'REJECTED', comment: 'Blurry scan' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/review`)
      .flush({ code: 'NO_VERSION_TO_REVIEW', message: 'Document has no uploaded version yet.', requestId: 'r1' }, { status: 400, statusText: 'Bad Request' });

    expect(fixture.componentInstance.error()).toBe('Document has no uploaded version yet.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('cancel() closes the dialog without a result', () => {
    const fixture = TestBed.createComponent(ReviewDocumentDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
