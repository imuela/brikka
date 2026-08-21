import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { CancelDialogComponent } from './cancel-dialog.component';

describe('CancelDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [CancelDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1' } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('submits reason/comment and closes the dialog with the cancelled case', () => {
    const fixture = TestBed.createComponent(CancelDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ reason: 'CLIENT_REQUEST', comment: 'client withdrew' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/cancel`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'CLIENT_REQUEST', comment: 'client withdrew' });
    req.flush({ id: 'k1', status: 'CANCELLED' });

    expect(dialogRef.close).toHaveBeenCalledWith({ id: 'k1', status: 'CANCELLED' });
  });

  it('does not submit without a selected reason', () => {
    const fixture = TestBed.createComponent(CancelDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/cancel`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error on failure', () => {
    const fixture = TestBed.createComponent(CancelDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ reason: 'OTHER', comment: '' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/cancel`)
      .flush({ code: 'INVALID_TRANSITION', message: 'Case already cancelled.', requestId: 'r1' }, { status: 409, statusText: 'Conflict' });

    expect(fixture.componentInstance.error()).toBe('No es posible realizar ese cambio de estado en este momento.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('rejects a comment longer than the backend limit before submitting', () => {
    const fixture = TestBed.createComponent(CancelDialogComponent);
    fixture.detectChanges();
    const tooLong = 'x'.repeat(fixture.componentInstance.maxCommentLength + 1);
    fixture.componentInstance.form.setValue({ reason: 'OTHER', comment: tooLong });

    expect(fixture.componentInstance.form.controls.comment.hasError('maxlength')).toBe(true);

    fixture.componentInstance.submit();
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/cancel`);
  });

  it('accepts a comment right at the backend limit', () => {
    const fixture = TestBed.createComponent(CancelDialogComponent);
    fixture.detectChanges();
    const atLimit = 'x'.repeat(fixture.componentInstance.maxCommentLength);
    fixture.componentInstance.form.setValue({ reason: 'OTHER', comment: atLimit });

    expect(fixture.componentInstance.form.controls.comment.hasError('maxlength')).toBe(false);
  });

  it('cancelDialog() closes the dialog without a result', () => {
    const fixture = TestBed.createComponent(CancelDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancelDialog();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
